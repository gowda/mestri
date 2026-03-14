package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RedisScheduledJobStoreTest {

  @Test
  fun `persist scheduled stores record and zset entry`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val store =
      RedisScheduledJobStore(
        jobRecordStore =
          RedisJobRecordStore(
            hashAccessor = hashAccessor,
            mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
          ),
        sortedSetAccessor = sortedSetAccessor,
        clock = { Instant.parse("2026-03-14T10:00:00Z") },
      )
    val payload = samplePayload()
    val dueAt = Instant.parse("2026-03-14T10:05:00Z")

    val result = store.persistScheduled(payload, dueAt)
    val storedRecord =
      RedisJobRecordStore(
          hashAccessor = hashAccessor,
          mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
        )
        .find(payload.jobId)

    assertEquals(JobBucket.SCHEDULED, result.bucket)
    assertEquals("mestri:scheduled", result.bucketKey)
    assertEquals(dueAt, result.dueAt)
    assertEquals(listOf(ScoredValue(payload.jobId.value, dueAt.toEpochMilli().toDouble())), sortedSetAccessor.valuesFor(result.bucketKey))

    assertNotNull(storedRecord)
    assertEquals(JobBucket.SCHEDULED, storedRecord.bucket)
  }

  @Test
  fun `persist retry stores record and zset entry`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val store =
      RedisScheduledJobStore(
        jobRecordStore =
          RedisJobRecordStore(
            hashAccessor = hashAccessor,
            mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
          ),
        sortedSetAccessor = sortedSetAccessor,
      )
    val payload = samplePayload()
    val dueAt = Instant.parse("2026-03-14T10:15:00Z")

    val result = store.persistRetry(payload, dueAt)

    assertEquals(JobBucket.RETRY, result.bucket)
    assertEquals("mestri:retry", result.bucketKey)
    assertEquals(listOf(ScoredValue(payload.jobId.value, dueAt.toEpochMilli().toDouble())), sortedSetAccessor.valuesFor(result.bucketKey))
  }

  private fun samplePayload(): JobPayload =
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

  private data class ScoredValue(
    val value: String,
    val score: Double,
  )

  private class InMemoryRedisSortedSetAccessor : RedisSortedSetAccessor {
    private val data = mutableMapOf<String, MutableList<ScoredValue>>()

    override fun add(key: String, value: String, score: Double) {
      data.getOrPut(key) { mutableListOf() }.add(ScoredValue(value, score))
    }

    fun valuesFor(key: String): List<ScoredValue> = data[key].orEmpty()
  }
}
