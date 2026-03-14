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

class RedisScheduledJobPromoterIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `promote due scheduled moves due jobs into queue in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)
    val jobRecordStore = newJobRecordStore(redisTemplate)
    val sortedSetAccessor = newSortedSetAccessor(redisTemplate)
    val scheduledStore =
      RedisScheduledJobStore(
        jobRecordStore = jobRecordStore,
        sortedSetAccessor = sortedSetAccessor,
        clock = { Instant.parse("2026-03-15T12:00:00Z") },
      )
    val promoter =
      RedisScheduledJobPromoter(
        jobRecordStore = jobRecordStore,
        sortedSetAccessor = sortedSetAccessor,
        listAccessor = SpringRedisListAccessor(redisTemplate),
      )
    val duePayload = samplePayload("job-due")
    val futurePayload = samplePayload("job-future")

    scheduledStore.persistScheduled(duePayload, Instant.parse("2026-03-15T12:05:00Z"))
    scheduledStore.persistScheduled(futurePayload, Instant.parse("2026-03-15T12:15:00Z"))

    val result = promoter.promoteDueScheduled(now = Instant.parse("2026-03-15T12:10:00Z"))
    val queueEntries =
      redisTemplate
        .opsForList()
        .range(RedisKeyspace.queue(duePayload.queue), 0, -1)
        .orEmpty()
    val scheduledEntries =
      redisTemplate
        .opsForZSet()
        .range(RedisKeyspace.scheduled(), 0, -1)
        .orEmpty()

    assertEquals(listOf(JobId("job-due")), result.promotedJobIds)
    assertEquals(emptyList(), result.skippedJobIds)
    assertEquals(JobBucket.QUEUED, jobRecordStore.find(JobId("job-due"))?.bucket)
    assertEquals(JobBucket.SCHEDULED, jobRecordStore.find(JobId("job-future"))?.bucket)
    assertEquals(listOf("job-due"), queueEntries)
    assertEquals(setOf("job-future"), scheduledEntries)
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
