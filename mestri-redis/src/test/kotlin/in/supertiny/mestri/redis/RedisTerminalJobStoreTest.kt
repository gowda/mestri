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

class RedisTerminalJobStoreTest {

  @Test
  fun `persist succeeded stores terminal record and zset marker`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val store =
      RedisTerminalJobStore(
        jobRecordStore =
          RedisJobRecordStore(
            hashAccessor = hashAccessor,
            mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
          ),
        sortedSetAccessor = sortedSetAccessor,
      )
    val payload = samplePayload()
    val recordedAt = Instant.parse("2026-03-15T10:00:00Z")

    val result = store.persistSucceeded(payload, recordedAt)
    val storedRecord =
      RedisJobRecordStore(
          hashAccessor = hashAccessor,
          mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
        )
        .find(payload.jobId)

    assertEquals(JobBucket.SUCCEEDED, result.bucket)
    assertEquals("mestri:succeeded", result.bucketKey)
    assertEquals(listOf(ScoredValue(payload.jobId.value, recordedAt.toEpochMilli().toDouble())), sortedSetAccessor.valuesFor(result.bucketKey))

    assertNotNull(storedRecord)
    assertEquals(JobBucket.SUCCEEDED, storedRecord.bucket)
  }

  @Test
  fun `persist deleted stores terminal record and zset marker`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val store =
      RedisTerminalJobStore(
        jobRecordStore =
          RedisJobRecordStore(
            hashAccessor = hashAccessor,
            mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
          ),
        sortedSetAccessor = sortedSetAccessor,
      )
    val payload = samplePayload()
    val recordedAt = Instant.parse("2026-03-15T10:02:00Z")

    val result = store.persistDeleted(payload, recordedAt)

    assertEquals(JobBucket.DELETED, result.bucket)
    assertEquals("mestri:deleted", result.bucketKey)
    assertEquals(listOf(ScoredValue(payload.jobId.value, recordedAt.toEpochMilli().toDouble())), sortedSetAccessor.valuesFor(result.bucketKey))
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
