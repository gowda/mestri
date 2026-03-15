package `in`.supertiny.mestri.spring.boot.autoconfigure

import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.scheduling.RecurringJobScheduler
import `in`.supertiny.mestri.core.serialization.JobPayload
import `in`.supertiny.mestri.redis.RedisImmediateJobEnqueuer
import `in`.supertiny.mestri.redis.RedisScheduledJobPromoter
import org.springframework.context.SmartLifecycle
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MestriSchedulerLifecycle(
  private val properties: MestriProperties.Scheduler,
  private val scheduledJobPromoter: RedisScheduledJobPromoter,
  private val recurringJobScheduler: RecurringJobScheduler,
  private val immediateJobEnqueuer: RedisImmediateJobEnqueuer,
  private val clock: Clock,
) : SmartLifecycle {
  private val running = AtomicBoolean(false)

  @Volatile
  private var executor: ScheduledExecutorService? = null

  override fun start() {
    if (!running.compareAndSet(false, true)) return

    val lifecycleExecutor = Executors.newScheduledThreadPool(2)
    executor = lifecycleExecutor

    lifecycleExecutor.scheduleWithFixedDelay(
      ::promoteDueJobs,
      0L,
      properties.promotionInterval.toMillis(),
      TimeUnit.MILLISECONDS,
    )
    lifecycleExecutor.scheduleWithFixedDelay(
      ::enqueueDueRecurringJobs,
      0L,
      properties.recurringCheckInterval.toMillis(),
      TimeUnit.MILLISECONDS,
    )
  }

  override fun stop() {
    if (!running.compareAndSet(true, false)) return

    executor?.shutdown()
    executor?.awaitTermination(5, TimeUnit.SECONDS)
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

  override fun getPhase(): Int = 10

  private fun promoteDueJobs() {
    scheduledJobPromoter.promoteDueScheduled()
    scheduledJobPromoter.promoteDueRetry()
  }

  private fun enqueueDueRecurringJobs() {
    val now = Instant.now(clock)

    recurringJobScheduler.dueDefinitions(now).forEach { definition ->
      immediateJobEnqueuer.enqueue(
        JobPayload(
          jobId = JobId("recurring:${definition.id.value}:${UUID.randomUUID()}"),
          invocation = definition.invocation,
          queue = definition.queue,
          retryPolicy = definition.retryPolicy,
          labels = definition.labels,
          metadata =
            definition.metadata + mapOf("mestri.recurringJobId" to definition.id.value),
          deduplicationKey = definition.deduplicationKey,
          deduplicationPolicy = definition.deduplicationPolicy,
        ),
      )
      recurringJobScheduler.markEnqueued(definition, enqueuedAt = now)
    }
  }
}
