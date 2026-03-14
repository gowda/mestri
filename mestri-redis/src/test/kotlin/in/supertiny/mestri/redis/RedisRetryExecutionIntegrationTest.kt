package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.middleware.ServerExecutionResult
import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.RetryBackoffCalculator
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

class RedisRetryExecutionIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `retryable execution persists retry record and removes lease in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val jobRecordStore = newJobRecordStore(redisTemplate)
    val leaseStore = newLeaseStore(redisTemplate)
    val payload = samplePayload()
    val enqueuer =
      RedisImmediateJobEnqueuer(
        jobRecordStore = jobRecordStore,
        listAccessor = SpringRedisListAccessor(redisTemplate),
        clock = { Instant.parse("2026-03-15T12:00:00Z") },
      )
    val reservationStore =
      RedisQueueReservationStore(
        queueAccessor = SpringRedisQueueAccessor(redisTemplate),
        jobRecordStore = jobRecordStore,
        leaseStore = leaseStore,
        clock = { Instant.parse("2026-03-15T12:01:00Z") },
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
        backoffCalculator = RetryBackoffCalculator(),
        clock = { Instant.parse("2026-03-15T12:02:00Z") },
      )

    enqueuer.enqueue(payload)
    val reservation =
      reservationStore
        .reserve(
          queue = payload.queue,
          workerId = WorkerId("worker-1"),
          leaseDuration = Duration.ofMinutes(5),
        )?.reservation

    assertNotNull(reservation)

    val scheduling =
      retryHandler.handle(
        reservation = reservation,
        attempt = 1,
        result =
          ServerExecutionResult(
            outcome = ServerExecutionOutcome.RETRYABLE_FAILURE,
            metadata = mapOf("traceId" to "trace-1"),
            errorMessage = "temporary outage",
          ),
      )

    val updatedRecord = jobRecordStore.find(payload.jobId)
    val lease = leaseStore.find(payload.jobId)
    val retryScore =
      redisTemplate.opsForZSet().score(RedisKeyspace.retry(), payload.jobId.value)

    assertEquals(2, scheduling.attempt)
    assertEquals(Instant.parse("2026-03-15T12:02:05Z"), scheduling.dueAt)
    assertNotNull(updatedRecord)
    assertEquals(JobBucket.RETRY, updatedRecord.bucket)
    assertEquals("trace-1", updatedRecord.payload.metadata["traceId"])
    assertEquals(
      "temporary outage",
      updatedRecord.payload.metadata[RedisRetryExecutionHandler.METADATA_LAST_ERROR_MESSAGE],
    )
    assertNull(lease)
    assertEquals(Instant.parse("2026-03-15T12:02:05Z").toEpochMilli().toDouble(), retryScore)
  }

  private fun samplePayload(): JobPayload =
    JobPayload(
      jobId = JobId("job-retry-1"),
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
}
