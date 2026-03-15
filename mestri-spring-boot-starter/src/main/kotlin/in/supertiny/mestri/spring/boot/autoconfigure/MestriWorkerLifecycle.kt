package `in`.supertiny.mestri.spring.boot.autoconfigure

import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.runtime.ProcessId
import `in`.supertiny.mestri.core.runtime.ServerExecutionPipeline
import `in`.supertiny.mestri.core.runtime.WorkerExecutionCoordinator
import `in`.supertiny.mestri.core.runtime.WorkerHeartbeat
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.redis.RedisDeadLetterExecutionHandler
import `in`.supertiny.mestri.redis.RedisQueueReservationStore
import `in`.supertiny.mestri.redis.RedisRetryExecutionHandler
import `in`.supertiny.mestri.redis.RedisSuccessfulExecutionHandler
import `in`.supertiny.mestri.redis.RedisWorkerHeartbeatStore
import org.springframework.context.SmartLifecycle
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MestriWorkerLifecycle(
  private val properties: MestriProperties.Worker,
  private val reservationStore: RedisQueueReservationStore,
  private val executionPipeline: ServerExecutionPipeline,
  private val successHandler: RedisSuccessfulExecutionHandler,
  private val retryHandler: RedisRetryExecutionHandler,
  private val deadLetterHandler: RedisDeadLetterExecutionHandler,
  private val heartbeatStore: RedisWorkerHeartbeatStore,
  private val clock: Clock,
) : SmartLifecycle {
  private val running = AtomicBoolean(false)
  private val processId = ProcessId("mestri-process-${UUID.randomUUID()}")
  private val workerIds =
    (1..properties.concurrency).map { index ->
      WorkerId("${processId.value}-$index")
    }
  private val coordinators =
    workerIds.associateWith { workerId ->
      WorkerExecutionCoordinator(
        reservationSource = {
          properties.queues.firstNotNullOfOrNull { queueName ->
            reservationStore
              .reserve(
                queue = QueueName(queueName),
                workerId = workerId,
                leaseDuration = properties.leaseDuration,
              )?.reservation
          }
        },
        reservationProcessor = { reservation ->
          val attempt =
            reservation.payload.metadata[RedisRetryExecutionHandler.METADATA_NEXT_ATTEMPT]
              ?.toIntOrNull() ?: 1
          val result = executionPipeline.execute(reservation, attempt = attempt)

          when (result.outcome) {
            ServerExecutionOutcome.SUCCEEDED ->
              successHandler.handle(reservation, result)

            ServerExecutionOutcome.RETRYABLE_FAILURE ->
              if (attempt < reservation.payload.retryPolicy.maxAttempts) {
                retryHandler.handle(
                  reservation = reservation,
                  result = result,
                  attempt = attempt,
                )
              } else {
                deadLetterHandler.handle(
                  reservation = reservation,
                  result = result,
                  attempt = attempt,
                )
              }

            ServerExecutionOutcome.TERMINAL_FAILURE ->
              deadLetterHandler.handle(
                reservation = reservation,
                result = result,
                attempt = attempt,
              )
          }
        },
      )
    }

  @Volatile
  private var executor: ScheduledExecutorService? = null

  override fun start() {
    if (!running.compareAndSet(false, true)) return

    val lifecycleExecutor =
      Executors.newScheduledThreadPool(
        properties.concurrency + 1,
      )
    executor = lifecycleExecutor

    workerIds.forEach { workerId ->
      lifecycleExecutor.scheduleWithFixedDelay(
        {
          coordinators.getValue(workerId).runNext()
        },
        0L,
        properties.pollInterval.toMillis(),
        TimeUnit.MILLISECONDS,
      )
    }

    lifecycleExecutor.scheduleWithFixedDelay(
      ::publishHeartbeats,
      0L,
      properties.heartbeatInterval.toMillis(),
      TimeUnit.MILLISECONDS,
    )
  }

  override fun stop() {
    if (!running.compareAndSet(true, false)) return

    coordinators.values.forEach(WorkerExecutionCoordinator::requestShutdown)
    executor?.shutdown()
    executor?.awaitTermination(properties.shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
    workerIds.forEach(heartbeatStore::delete)
    executor = null
  }

  override fun stop(callback: Runnable) {
    try {
      stop()
    } finally {
      callback.run()
    }
  }

  override fun isRunning(): Boolean = running.get()

  override fun isAutoStartup(): Boolean = true

  override fun getPhase(): Int = 0

  private fun publishHeartbeats() {
    val recordedAt = Instant.now(clock)
    val expiresAt = recordedAt.plus(properties.heartbeatInterval.multipliedBy(2))

    workerIds.forEach { workerId ->
      heartbeatStore.save(
        WorkerHeartbeat(
          workerId = workerId,
          processId = processId,
          queues = properties.queues.toSet(),
          recordedAt = recordedAt,
          expiresAt = expiresAt,
        ),
      )
    }
  }
}
