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

class RedisDeadLetterExecutionIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `terminal execution persists dead record and removes lease in real redis`() {
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
    val deadLetterHandler =
      RedisDeadLetterExecutionHandler(
        scheduledJobStore =
          RedisScheduledJobStore(
            jobRecordStore = jobRecordStore,
            sortedSetAccessor = newSortedSetAccessor(redisTemplate),
          ),
        terminalJobStore =
          RedisTerminalJobStore(
            jobRecordStore = jobRecordStore,
            sortedSetAccessor = newSortedSetAccessor(redisTemplate),
          ),
        leaseStore = leaseStore,
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

    deadLetterHandler.handle(
      reservation = reservation,
      attempt = 1,
      result =
        ServerExecutionResult(
          outcome = ServerExecutionOutcome.TERMINAL_FAILURE,
          metadata = mapOf("traceId" to "trace-1"),
          errorMessage = "fatal outage",
        ),
    )

    val updatedRecord = jobRecordStore.find(payload.jobId)
    val lease = leaseStore.find(payload.jobId)
    val deadScore =
      redisTemplate.opsForZSet().score(RedisKeyspace.dead(), payload.jobId.value)

    assertNotNull(updatedRecord)
    assertEquals(JobBucket.DEAD, updatedRecord.bucket)
    assertEquals("trace-1", updatedRecord.payload.metadata["traceId"])
    assertEquals(
      "fatal outage",
      updatedRecord.payload.metadata[RedisDeadLetterExecutionHandler.METADATA_LAST_ERROR_MESSAGE],
    )
    assertNull(lease)
    assertEquals(Instant.parse("2026-03-15T12:02:00Z").toEpochMilli().toDouble(), deadScore)
  }

  private fun samplePayload(): JobPayload =
    JobPayload(
      jobId = JobId("job-dead-1"),
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
