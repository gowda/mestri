package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.middleware.ServerExecutionResult
import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
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

class RedisSuccessfulExecutionHandlerTest {
  @Test
  fun `successful execution moves job to succeeded and clears lease`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val jobRecordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val leaseStore = RedisJobLeaseStore(hashAccessor, InMemoryRedisSetAccessor())
    val terminalStore = RedisTerminalJobStore(jobRecordStore, sortedSetAccessor)
    val handler =
      RedisSuccessfulExecutionHandler(
        terminalJobStore = terminalStore,
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

    val execution =
      handler.handle(
        reservation = reservation,
        result =
          ServerExecutionResult(
            outcome = ServerExecutionOutcome.SUCCEEDED,
            metadata = mapOf("traceId" to "trace-1"),
          ),
      )
    val storedRecord = jobRecordStore.find(reservation.payload.jobId)

    assertEquals(reservation.payload.jobId, execution.jobId)
    assertEquals(Instant.parse("2026-03-15T12:10:00Z"), execution.completedAt)
    assertNotNull(storedRecord)
    assertEquals(JobBucket.SUCCEEDED, storedRecord.bucket)
    assertEquals("trace-1", storedRecord.payload.metadata["traceId"])
    assertNull(leaseStore.find(reservation.payload.jobId))
  }

  @Test
  fun `successful execution handler rejects non success result`() {
    val handler =
      RedisSuccessfulExecutionHandler(
        terminalJobStore =
          RedisTerminalJobStore(
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
        reservation = sampleReservation(),
        result = ServerExecutionResult(outcome = ServerExecutionOutcome.RETRYABLE_FAILURE),
      )
    }
  }

  private fun sampleReservation(): JobReservation =
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
