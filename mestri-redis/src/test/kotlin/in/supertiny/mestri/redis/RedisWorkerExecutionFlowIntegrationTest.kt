package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.middleware.ServerExecutionResult
import `in`.supertiny.mestri.core.middleware.ServerMiddleware
import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.JobHandlerInvoker
import `in`.supertiny.mestri.core.runtime.JobReservation
import `in`.supertiny.mestri.core.runtime.JobReservationProcessor
import `in`.supertiny.mestri.core.runtime.JobReservationSource
import `in`.supertiny.mestri.core.runtime.ServerExecutionPipeline
import `in`.supertiny.mestri.core.runtime.WorkerCycleOutcome
import `in`.supertiny.mestri.core.runtime.WorkerExecutionCoordinator
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedisWorkerExecutionFlowIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `worker execution completes job into succeeded bucket`() {
    val fixture = newFixture()
    val payload = samplePayload(jobId = "job-success-1")
    fixture.enqueuer.enqueue(payload)

    val coordinator =
      WorkerExecutionCoordinator(
        reservationSource = fixture.reservationSource(),
        reservationProcessor =
          JobReservationProcessor { reservation ->
            val result = fixture.successPipeline.execute(reservation, attempt = 1)
            fixture.successHandler.handle(reservation, result)
          },
      )

    val cycleResult = coordinator.runNext()
    val record = fixture.jobRecordStore.find(payload.jobId)
    val lease = fixture.leaseStore.find(payload.jobId)

    assertEquals(WorkerCycleOutcome.PROCESSED, cycleResult.outcome)
    assertNotNull(record)
    assertEquals(JobBucket.SUCCEEDED, record.bucket)
    assertEquals("trace-1", record.payload.metadata["traceId"])
    assertNull(lease)
  }

  @Test
  fun `worker execution schedules retry for retryable failure`() {
    val fixture = newFixture()
    val payload = samplePayload(jobId = "job-retry-1")
    fixture.enqueuer.enqueue(payload)

    val coordinator =
      WorkerExecutionCoordinator(
        reservationSource = fixture.reservationSource(),
        reservationProcessor =
          JobReservationProcessor { reservation ->
            val result = fixture.retryPipeline.execute(reservation, attempt = 1)
            fixture.retryHandler.handle(reservation, result, attempt = 1)
          },
      )

    val cycleResult = coordinator.runNext()
    val record = fixture.jobRecordStore.find(payload.jobId)
    val lease = fixture.leaseStore.find(payload.jobId)
    val retryScore =
      fixture.redisTemplate.opsForZSet().score(
        RedisKeyspace.retry(),
        payload.jobId.value,
      )

    assertEquals(WorkerCycleOutcome.PROCESSED, cycleResult.outcome)
    assertNotNull(record)
    assertEquals(JobBucket.RETRY, record.bucket)
    assertEquals(
      "temporary outage",
      record.payload.metadata[RedisRetryExecutionHandler.METADATA_LAST_ERROR_MESSAGE],
    )
    assertEquals(Instant.parse("2026-03-15T12:02:05Z").toEpochMilli().toDouble(), retryScore)
    assertNull(lease)
  }

  @Test
  fun `worker execution dead letters terminal failure`() {
    val fixture = newFixture()
    val payload = samplePayload(jobId = "job-dead-1")
    fixture.enqueuer.enqueue(payload)

    val coordinator =
      WorkerExecutionCoordinator(
        reservationSource = fixture.reservationSource(),
        reservationProcessor =
          JobReservationProcessor { reservation ->
            val result = fixture.deadPipeline.execute(reservation, attempt = 1)
            fixture.deadHandler.handle(reservation, result, attempt = 1)
          },
      )

    val cycleResult = coordinator.runNext()
    val record = fixture.jobRecordStore.find(payload.jobId)
    val lease = fixture.leaseStore.find(payload.jobId)
    val deadScore =
      fixture.redisTemplate.opsForZSet().score(
        RedisKeyspace.dead(),
        payload.jobId.value,
      )

    assertEquals(WorkerCycleOutcome.PROCESSED, cycleResult.outcome)
    assertNotNull(record)
    assertEquals(JobBucket.DEAD, record.bucket)
    assertEquals(
      "fatal outage",
      record.payload.metadata[RedisDeadLetterExecutionHandler.METADATA_LAST_ERROR_MESSAGE],
    )
    assertEquals(Instant.parse("2026-03-15T12:02:00Z").toEpochMilli().toDouble(), deadScore)
    assertNull(lease)
  }

  @Test
  fun `worker shutdown stops new reservations while preserving queued work`() {
    val fixture = newFixture()
    val firstPayload = samplePayload(jobId = "job-shutdown-1")
    val secondPayload = samplePayload(jobId = "job-shutdown-2")
    fixture.enqueuer.enqueue(firstPayload)
    fixture.enqueuer.enqueue(secondPayload)

    lateinit var coordinator: WorkerExecutionCoordinator
    coordinator =
      WorkerExecutionCoordinator(
        reservationSource = fixture.reservationSource(),
        reservationProcessor =
          JobReservationProcessor { reservation ->
            coordinator.requestShutdown()
            val result = fixture.successPipeline.execute(reservation, attempt = 1)
            fixture.successHandler.handle(reservation, result)
          },
      )

    val firstCycle = coordinator.runNext()
    val secondCycle = coordinator.runNext()
    val firstRecord = fixture.jobRecordStore.find(firstPayload.jobId)
    val secondRecord = fixture.jobRecordStore.find(secondPayload.jobId)
    val remainingQueueEntries =
      fixture.redisTemplate
        .opsForList()
        .range(
          RedisKeyspace.queue(firstPayload.queue),
          0,
          -1,
        ).orEmpty()

    assertEquals(WorkerCycleOutcome.PROCESSED, firstCycle.outcome)
    assertEquals(WorkerCycleOutcome.STOPPED, secondCycle.outcome)
    assertNotNull(firstRecord)
    assertEquals(JobBucket.SUCCEEDED, firstRecord.bucket)
    assertNotNull(secondRecord)
    assertEquals(JobBucket.QUEUED, secondRecord.bucket)
    assertEquals(listOf(secondPayload.jobId.value), remainingQueueEntries)
  }

  private fun newFixture(): WorkerFixture {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)
    val jobRecordStore = newJobRecordStore(redisTemplate)
    val leaseStore = newLeaseStore(redisTemplate)
    val reservationStore =
      RedisQueueReservationStore(
        queueAccessor = SpringRedisQueueAccessor(redisTemplate),
        jobRecordStore = jobRecordStore,
        leaseStore = leaseStore,
        clock = { Instant.parse("2026-03-15T12:01:00Z") },
      )
    val successHandler =
      RedisSuccessfulExecutionHandler(
        terminalJobStore =
          RedisTerminalJobStore(
            jobRecordStore,
            newSortedSetAccessor(redisTemplate),
          ),
        leaseStore = leaseStore,
        clock = { Instant.parse("2026-03-15T12:02:00Z") },
      )
    val retryHandler =
      RedisRetryExecutionHandler(
        scheduledJobStore =
          RedisScheduledJobStore(
            jobRecordStore = jobRecordStore,
            sortedSetAccessor = newSortedSetAccessor(redisTemplate),
            clock = { Instant.parse("2026-03-15T12:02:00Z") },
          ),
        leaseStore = leaseStore,
        clock = { Instant.parse("2026-03-15T12:02:00Z") },
      )
    val deadHandler =
      RedisDeadLetterExecutionHandler(
        scheduledJobStore =
          RedisScheduledJobStore(
            jobRecordStore = jobRecordStore,
            sortedSetAccessor = newSortedSetAccessor(redisTemplate),
          ),
        terminalJobStore =
          RedisTerminalJobStore(
            jobRecordStore,
            newSortedSetAccessor(redisTemplate),
          ),
        leaseStore = leaseStore,
        clock = { Instant.parse("2026-03-15T12:02:00Z") },
      )

    return WorkerFixture(
      redisTemplate = redisTemplate,
      jobRecordStore = jobRecordStore,
      leaseStore = leaseStore,
      enqueuer =
        RedisImmediateJobEnqueuer(
          jobRecordStore = jobRecordStore,
          listAccessor = SpringRedisListAccessor(redisTemplate),
          clock = { Instant.parse("2026-03-15T12:00:00Z") },
        ),
      successPipeline =
        ServerExecutionPipeline(
          middlewares =
            listOf(
              ServerMiddleware { request, next ->
                next.proceed(request.copy(metadata = request.metadata + ("traceId" to "trace-1")))
              },
            ),
          handlerInvoker = JobHandlerInvoker { _, _ -> },
        ),
      retryPipeline =
        ServerExecutionPipeline(
          middlewares =
            listOf(
              ServerMiddleware { request, _ ->
                ServerExecutionResult(
                  outcome = ServerExecutionOutcome.RETRYABLE_FAILURE,
                  metadata = request.metadata + ("traceId" to "trace-1"),
                  errorMessage = "temporary outage",
                )
              },
            ),
          handlerInvoker = JobHandlerInvoker { _, _ -> },
        ),
      deadPipeline =
        ServerExecutionPipeline(
          middlewares = emptyList(),
          handlerInvoker = JobHandlerInvoker { _, _ -> error("fatal outage") },
        ),
      successHandler = successHandler,
      retryHandler = retryHandler,
      deadHandler = deadHandler,
      reservationSupplier = {
        reservationStore
          .reserve(
            queue = QueueName("billing"),
            workerId = WorkerId("worker-1"),
            leaseDuration = Duration.ofMinutes(5),
          )?.reservation
      },
    )
  }

  private fun samplePayload(jobId: String): JobPayload =
    JobPayload(
      jobId = JobId(jobId),
      invocation =
        InvocationDescriptor(
          target =
            SpringBeanTarget(
              className = "io.example.jobs.InvoiceJobs",
              beanName = "invoiceJobs",
            ),
          method =
            InvocationMethod(
              name = "generateInvoice",
              parameterTypeNames = listOf("java.lang.String"),
            ),
          arguments = listOf(InvocationArgument("java.lang.String", "\"user-1\"")),
        ),
      queue = QueueName("billing"),
      retryPolicy =
        RetryPolicy(
          maxAttempts = 5,
          initialDelay = 5.seconds,
          maxDelay = 2.minutes,
          backoffMultiplier = 2.0,
          useJitter = false,
        ),
      metadata = mapOf("requestId" to "request-1"),
    )

  private data class WorkerFixture(
    val redisTemplate: org.springframework.data.redis.core.StringRedisTemplate,
    val jobRecordStore: RedisJobRecordStore,
    val leaseStore: RedisJobLeaseStore,
    val enqueuer: RedisImmediateJobEnqueuer,
    val successPipeline: ServerExecutionPipeline,
    val retryPipeline: ServerExecutionPipeline,
    val deadPipeline: ServerExecutionPipeline,
    val successHandler: RedisSuccessfulExecutionHandler,
    val retryHandler: RedisRetryExecutionHandler,
    val deadHandler: RedisDeadLetterExecutionHandler,
    val reservationSupplier: () -> JobReservation?,
  ) {
    fun reservationSource(): JobReservationSource = JobReservationSource { reservationSupplier() }
  }
}
