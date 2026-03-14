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

class RedisImmediateJobEnqueuerTest {

  @Test
  fun `enqueue persists queued job record and appends job id to queue`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val listAccessor = InMemoryRedisListAccessor()
    val store =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val enqueuer =
      RedisImmediateJobEnqueuer(
        jobRecordStore = store,
        listAccessor = listAccessor,
        clock = { Instant.parse("2026-03-14T10:00:00Z") },
      )
    val payload = samplePayload()

    val result = enqueuer.enqueue(payload)
    val storedRecord = store.find(payload.jobId)

    assertEquals(payload.jobId, result.jobId)
    assertEquals(payload.queue, result.queue)
    assertEquals("mestri:queue:billing", result.queueKey)
    assertEquals(listOf(payload.jobId.value), listAccessor.valuesFor(result.queueKey))

    assertNotNull(storedRecord)
    assertEquals(JobBucket.QUEUED, storedRecord.bucket)
    assertEquals(payload, storedRecord.payload)
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
      metadata = mapOf("traceId" to "trace-1"),
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

  private class InMemoryRedisListAccessor : RedisListAccessor {
    private val data = mutableMapOf<String, MutableList<String>>()

    override fun rightPush(key: String, value: String) {
      data.getOrPut(key) { mutableListOf() }.add(value)
    }

    fun valuesFor(key: String): List<String> = data[key].orEmpty()
  }
}
