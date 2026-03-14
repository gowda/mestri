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

class RedisBucketPersistenceIntegrationTest : RedisIntegrationSupport() {

  @Test
  fun `scheduled and retry persistence write sorted set markers and job buckets`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val jobRecordStore = newJobRecordStore(redisTemplate)
    val store =
      RedisScheduledJobStore(
        jobRecordStore = jobRecordStore,
        sortedSetAccessor = newSortedSetAccessor(redisTemplate),
        clock = { Instant.parse("2026-03-15T10:00:00Z") },
      )

    val scheduledPayload = samplePayload("job-scheduled")
    val retryPayload = samplePayload("job-retry")
    val scheduledAt = Instant.parse("2026-03-15T10:05:00Z")
    val retryAt = Instant.parse("2026-03-15T10:10:00Z")

    store.persistScheduled(scheduledPayload, scheduledAt)
    store.persistRetry(retryPayload, retryAt)

    val scheduledEntries =
      redisTemplate.opsForZSet().rangeWithScores(RedisKeyspace.scheduled(), 0, -1).orEmpty()
    val retryEntries =
      redisTemplate.opsForZSet().rangeWithScores(RedisKeyspace.retry(), 0, -1).orEmpty()

    assertEquals(1, scheduledEntries.size)
    assertEquals("job-scheduled", scheduledEntries.first().value)
    assertEquals(scheduledAt.toEpochMilli().toDouble(), scheduledEntries.first().score)

    assertEquals(1, retryEntries.size)
    assertEquals("job-retry", retryEntries.first().value)
    assertEquals(retryAt.toEpochMilli().toDouble(), retryEntries.first().score)

    assertEquals(JobBucket.SCHEDULED, jobRecordStore.find(JobId("job-scheduled"))?.bucket)
    assertEquals(JobBucket.RETRY, jobRecordStore.find(JobId("job-retry"))?.bucket)
  }

  @Test
  fun `succeeded and deleted persistence write retention markers and job buckets`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val jobRecordStore = newJobRecordStore(redisTemplate)
    val store =
      RedisTerminalJobStore(
        jobRecordStore = jobRecordStore,
        sortedSetAccessor = newSortedSetAccessor(redisTemplate),
      )

    val succeededPayload = samplePayload("job-succeeded")
    val deletedPayload = samplePayload("job-deleted")
    val succeededAt = Instant.parse("2026-03-15T10:15:00Z")
    val deletedAt = Instant.parse("2026-03-15T10:20:00Z")

    store.persistSucceeded(succeededPayload, succeededAt)
    store.persistDeleted(deletedPayload, deletedAt)

    val succeededEntries =
      redisTemplate.opsForZSet().rangeWithScores(RedisKeyspace.succeeded(), 0, -1).orEmpty()
    val deletedEntries =
      redisTemplate.opsForZSet().rangeWithScores(RedisKeyspace.deleted(), 0, -1).orEmpty()

    assertEquals(1, succeededEntries.size)
    assertEquals("job-succeeded", succeededEntries.first().value)
    assertEquals(succeededAt.toEpochMilli().toDouble(), succeededEntries.first().score)

    assertEquals(1, deletedEntries.size)
    assertEquals("job-deleted", deletedEntries.first().value)
    assertEquals(deletedAt.toEpochMilli().toDouble(), deletedEntries.first().score)

    val succeededRecord = jobRecordStore.find(JobId("job-succeeded"))
    val deletedRecord = jobRecordStore.find(JobId("job-deleted"))

    assertNotNull(succeededRecord)
    assertEquals(JobBucket.SUCCEEDED, succeededRecord.bucket)
    assertNotNull(deletedRecord)
    assertEquals(JobBucket.DELETED, deletedRecord.bucket)
  }

  private fun samplePayload(jobId: String): JobPayload =
    JobPayload(
      jobId = JobId(jobId),
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
}
