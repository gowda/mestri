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
import `in`.supertiny.mestri.core.runtime.RetryBackoffCalculator
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

class RedisRetryExecutionHandlerTest {
  @Test
  fun `retryable execution moves job to retry bucket and clears lease`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val jobRecordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val leaseStore = RedisJobLeaseStore(hashAccessor, InMemoryRedisSetAccessor())
    val handler =
      RedisRetryExecutionHandler(
        scheduledJobStore = RedisScheduledJobStore(jobRecordStore, sortedSetAccessor),
        leaseStore = leaseStore,
        backoffCalculator = RetryBackoffCalculator(),
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

    val scheduling =
      handler.handle(
        reservation = reservation,
        attempt = 1,
        result =
          ServerExecutionResult(
            outcome = ServerExecutionOutcome.RETRYABLE_FAILURE,
            metadata = mapOf("traceId" to "trace-1"),
            errorMessage = "temporary outage",
          ),
      )
    val storedRecord = jobRecordStore.find(reservation.payload.jobId)

    assertEquals(2, scheduling.attempt)
    assertEquals(Instant.parse("2026-03-15T12:10:05Z"), scheduling.dueAt)
    assertNotNull(storedRecord)
    assertEquals(JobBucket.RETRY, storedRecord.bucket)
    assertEquals("trace-1", storedRecord.payload.metadata["traceId"])
    assertEquals(
      "temporary outage",
      storedRecord.payload.metadata[RedisRetryExecutionHandler.METADATA_LAST_ERROR_MESSAGE],
    )
    assertEquals(
      "2",
      storedRecord.payload.metadata[RedisRetryExecutionHandler.METADATA_NEXT_ATTEMPT],
    )
    assertNull(leaseStore.find(reservation.payload.jobId))
  }

  @Test
  fun `retry handler rejects exhausted retries`() {
    val handler =
      RedisRetryExecutionHandler(
        scheduledJobStore =
          RedisScheduledJobStore(
            RedisJobRecordStore(
              hashAccessor = InMemoryRedisHashAccessor(),
              mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
            ),
            InMemoryRedisSortedSetAccessor(),
          ),
        leaseStore = RedisJobLeaseStore(InMemoryRedisHashAccessor(), InMemoryRedisSetAccessor()),
      )

    assertFailsWith<IllegalArgumentException> {
      handler.handle(
        reservation =
          sampleReservation(
            retryPolicy = RetryPolicy(maxAttempts = 1, useJitter = false),
          ),
        attempt = 1,
        result =
          ServerExecutionResult(
            outcome = ServerExecutionOutcome.RETRYABLE_FAILURE,
            errorMessage = "boom",
          ),
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

    override fun putAll(
      key: String,
      fields: Map<String, String>,
    ) {
      data[key] = fields.toMap()
    }

    override fun delete(key: String) {
      data.remove(key)
    }
  }

  private class InMemoryRedisSortedSetAccessor : RedisSortedSetAccessor {
    override fun add(
      key: String,
      value: String,
      score: Double,
    ) = Unit

    override fun range(
      key: String,
      start: Long,
      end: Long,
    ): List<String> = emptyList()

    override fun rangeByScore(
      key: String,
      minScoreInclusive: Double,
      maxScoreInclusive: Double,
    ): List<String> = emptyList()

    override fun remove(
      key: String,
      value: String,
    ) = Unit
  }

  private class InMemoryRedisSetAccessor : RedisSetAccessor {
    private val data = mutableMapOf<String, MutableSet<String>>()

    override fun add(
      key: String,
      value: String,
    ) {
      data.getOrPut(key) { linkedSetOf() }.add(value)
    }

    override fun members(key: String): Set<String> = data[key].orEmpty()

    override fun remove(
      key: String,
      value: String,
    ) {
      data[key]?.remove(value)
    }
  }
}
