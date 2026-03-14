package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.DeduplicationKey
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
import kotlin.test.assertNull

class RedisJobRecordStoreTest {
  @Test
  fun `save and find round trip a redis job record`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val store =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val record = sampleRecord()

    store.save(record)

    val loaded = store.find(record.jobId)

    assertNotNull(loaded)
    assertEquals(record, loaded)
    assertEquals(
      RedisKeyspace.job(record.jobId),
      hashAccessor.lastWrittenKey,
    )
  }

  @Test
  fun `find returns null when job record is missing`() {
    val store =
      RedisJobRecordStore(
        hashAccessor = InMemoryRedisHashAccessor(),
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )

    assertNull(store.find(JobId("missing-job")))
  }

  private fun sampleRecord(): RedisJobRecord =
    RedisJobRecord(
      jobId = JobId("job-123"),
      bucket = JobBucket.SCHEDULED,
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
          metadata = mapOf("traceId" to "trace-1"),
          deduplicationKey = DeduplicationKey("invoice-user-1"),
        ),
      createdAt = Instant.parse("2026-03-14T10:00:00Z"),
      updatedAt = Instant.parse("2026-03-14T10:05:00Z"),
    )

  private class InMemoryRedisHashAccessor : RedisHashAccessor {
    private val data = mutableMapOf<String, Map<String, String>>()
    var lastWrittenKey: String? = null
      private set

    override fun entries(key: String): Map<String, String> = data[key].orEmpty()

    override fun putAll(
      key: String,
      fields: Map<String, String>,
    ) {
      lastWrittenKey = key
      data[key] = fields.toMap()
    }

    override fun delete(key: String) {
      data.remove(key)
    }
  }
}
