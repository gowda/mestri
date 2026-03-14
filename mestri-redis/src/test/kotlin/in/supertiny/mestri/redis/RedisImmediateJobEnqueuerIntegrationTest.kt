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

class RedisImmediateJobEnqueuerIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `immediate enqueue writes queue list entry and queued job record to redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val jobRecordStore = newJobRecordStore(redisTemplate)
    val enqueuer =
      RedisImmediateJobEnqueuer(
        jobRecordStore = jobRecordStore,
        listAccessor = SpringRedisListAccessor(redisTemplate),
        clock = { Instant.parse("2026-03-15T10:00:00Z") },
      )
    val payload = samplePayload()

    val result = enqueuer.enqueue(payload)
    val queueValues = redisTemplate.opsForList().range(result.queueKey, 0, -1).orEmpty()
    val storedRecord = jobRecordStore.find(payload.jobId)

    assertEquals("mestri:queue:billing", result.queueKey)
    assertEquals(listOf(payload.jobId.value), queueValues)

    assertNotNull(storedRecord)
    assertEquals(JobBucket.QUEUED, storedRecord.bucket)
    assertEquals(payload, storedRecord.payload)
  }

  private fun samplePayload(): JobPayload =
    JobPayload(
      jobId = JobId("job-integration-1"),
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
}
