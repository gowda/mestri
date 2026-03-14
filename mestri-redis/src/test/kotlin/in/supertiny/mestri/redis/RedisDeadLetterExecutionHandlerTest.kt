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
import `in`.supertiny.mestri.core.runtime.JobLease
import `in`.supertiny.mestri.core.runtime.JobReservation
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedisDeadLetterExecutionHandlerTest {

  @Test
  fun `terminal execution moves job to dead bucket and clears lease`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val jobRecordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val leaseStore = RedisJobLeaseStore(hashAccessor)
    val handler =
      RedisDeadLetterExecutionHandler(
        scheduledJobStore = RedisScheduledJobStore(jobRecordStore, sortedSetAccessor),
        terminalJobStore = RedisTerminalJobStore(jobRecordStore, sortedSetAccessor),
        leaseStore = leaseStore,
        clock = { Instant.parse("2026-03-15T12:10:00Z") },
      )
    val reservation = sampleReservation()

    jobRecordStore.save(
      RedisJobRecord(
        jobId = reservation.payload.jobId,
        bucket = JobBucket.RUNNING,
        payload = reservation.payload,
        createdAt = Instant.parse("2026-03-15T12:00:00Z"),
        updatedAt = Instant.parse("2026-03-15T12:01:00Z"),
      ),
    )
    leaseStore.save(reservation.lease)

    val deadLettering =
      handler.handle(
        reservation = reservation,
        attempt = 3,
        result =
          ServerExecutionResult(
            outcome = ServerExecutionOutcome.TERMINAL_FAILURE,
            metadata = mapOf("traceId" to "trace-1"),
            errorMessage = "fatal outage",
          ),
      )
    val storedRecord = jobRecordStore.find(reservation.payload.jobId)

    assertEquals(reservation.payload.jobId, deadLettering.jobId)
    assertEquals(Instant.parse("2026-03-15T12:10:00Z"), deadLettering.recordedAt)
    assertNotNull(storedRecord)
    assertEquals(JobBucket.DEAD, storedRecord.bucket)
    assertEquals("trace-1", storedRecord.payload.metadata["traceId"])
    assertEquals("fatal outage", storedRecord.payload.metadata[RedisDeadLetterExecutionHandler.METADATA_LAST_ERROR_MESSAGE])
    assertEquals("3", storedRecord.payload.metadata[RedisDeadLetterExecutionHandler.METADATA_FINAL_ATTEMPT])
    assertNull(leaseStore.find(reservation.payload.jobId))
  }

  @Test
  fun `exhausted retryable execution moves job to dead bucket`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val jobRecordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val leaseStore = RedisJobLeaseStore(hashAccessor)
    val handler =
      RedisDeadLetterExecutionHandler(
        scheduledJobStore = RedisScheduledJobStore(jobRecordStore, sortedSetAccessor),
        terminalJobStore = RedisTerminalJobStore(jobRecordStore, sortedSetAccessor),
        leaseStore = leaseStore,
        clock = { Instant.parse("2026-03-15T12:10:00Z") },
      )
    val reservation = sampleReservation(retryPolicy = RetryPolicy(maxAttempts = 2, initialDelay = 5.seconds, maxDelay = 2.minutes, useJitter = false))

    jobRecordStore.save(
      RedisJobRecord(
        jobId = reservation.payload.jobId,
        bucket = JobBucket.RUNNING,
        payload = reservation.payload,
        createdAt = Instant.parse("2026-03-15T12:00:00Z"),
        updatedAt = Instant.parse("2026-03-15T12:01:00Z"),
      ),
    )
    leaseStore.save(reservation.lease)

    val deadLettering =
      handler.handle(
        reservation = reservation,
        attempt = 2,
        result =
          ServerExecutionResult(
            outcome = ServerExecutionOutcome.RETRYABLE_FAILURE,
            errorMessage = "no retries left",
          ),
      )

    assertEquals(reservation.payload.jobId, deadLettering.jobId)
    assertEquals(JobBucket.DEAD, jobRecordStore.find(reservation.payload.jobId)?.bucket)
  }

  @Test
  fun `dead letter handler rejects non terminal non exhausted result`() {
    val handler =
      RedisDeadLetterExecutionHandler(
        scheduledJobStore =
          RedisScheduledJobStore(
            RedisJobRecordStore(
              hashAccessor = InMemoryRedisHashAccessor(),
              mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
            ),
            InMemoryRedisSortedSetAccessor(),
          ),
        terminalJobStore =
          RedisTerminalJobStore(
            RedisJobRecordStore(
              hashAccessor = InMemoryRedisHashAccessor(),
              mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
            ),
            InMemoryRedisSortedSetAccessor(),
          ),
        leaseStore = RedisJobLeaseStore(InMemoryRedisHashAccessor()),
      )

    assertFailsWith<IllegalArgumentException> {
      handler.handle(
        reservation = sampleReservation(),
        attempt = 1,
        result = ServerExecutionResult(outcome = ServerExecutionOutcome.RETRYABLE_FAILURE, errorMessage = "try again"),
      )
    }
  }

  private fun sampleReservation(
    retryPolicy: RetryPolicy =
      RetryPolicy(
        maxAttempts = 5,
        initialDelay = 5.seconds,
        maxDelay = 2.minutes,
        backoffMultiplier = 2.0,
        useJitter = false,
      ),
  ): JobReservation =
    JobReservation(
      lease =
        JobLease(
          jobId = JobId("job-123"),
          workerId = WorkerId("worker-1"),
          acquiredAt = Instant.parse("2026-03-15T12:01:00Z"),
          expiresAt = Instant.parse("2026-03-15T12:06:00Z"),
        ),
      payload =
        JobPayload(
          jobId = JobId("job-123"),
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
          retryPolicy = retryPolicy,
          metadata = mapOf("requestId" to "request-1"),
        ),
      reservedAt = Instant.parse("2026-03-15T12:01:00Z"),
    )

  private class InMemoryRedisHashAccessor : RedisHashAccessor {
    private val data = mutableMapOf<String, Map<String, String>>()

    override fun entries(key: String): Map<String, String> = data[key].orEmpty()

    override fun putAll(key: String, fields: Map<String, String>) {
      data[key] = fields.toMap()
    }

    override fun delete(key: String) {
      data.remove(key)
    }
  }

  private class InMemoryRedisSortedSetAccessor : RedisSortedSetAccessor {
    override fun add(key: String, value: String, score: Double) = Unit
  }
}
