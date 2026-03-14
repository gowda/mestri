package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.CleanupRetainedBucket
import `in`.supertiny.mestri.core.runtime.MaintenancePlan
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RedisRetentionCleanupIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `retention cleanup trims succeeded markers and deletes old records in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)
    val jobRecordStore = newJobRecordStore(redisTemplate)
    val sortedSetAccessor = newSortedSetAccessor(redisTemplate)
    val terminalStore = RedisTerminalJobStore(jobRecordStore, sortedSetAccessor)
    val executor =
      RedisMaintenanceExecutor(
        jobRecordStore = jobRecordStore,
        listAccessor = SpringRedisListAccessor(redisTemplate),
        scheduledJobStore = RedisScheduledJobStore(jobRecordStore, sortedSetAccessor),
        sortedSetAccessor = sortedSetAccessor,
        leaseStore = newLeaseStore(redisTemplate),
        heartbeatStore = newHeartbeatStore(redisTemplate),
      )

    terminalStore.persistSucceeded(samplePayload("job-old"), Instant.parse("2026-03-15T12:00:00Z"))
    terminalStore.persistSucceeded(samplePayload("job-new"), Instant.parse("2026-03-15T12:10:00Z"))

    executor.execute(
      MaintenancePlan(
        createdAt = Instant.parse("2026-03-15T12:15:00Z"),
        actions =
          listOf(
            CleanupRetainedBucket(RedisMaintenanceExecutor.BUCKET_SUCCEEDED, maxEntries = 1),
          ),
      ),
      now = Instant.parse("2026-03-15T12:15:00Z"),
    )

    val entries = redisTemplate.opsForZSet().range(RedisKeyspace.succeeded(), 0, -1).orEmpty()
    assertEquals(setOf("job-new"), entries)
    assertNull(jobRecordStore.find(JobId("job-old")))
    assertEquals(JobId("job-new"), jobRecordStore.find(JobId("job-new"))?.jobId)
  }

  @Test
  fun `retention cleanup trims retry markers and deletes old records in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)
    val jobRecordStore = newJobRecordStore(redisTemplate)
    val sortedSetAccessor = newSortedSetAccessor(redisTemplate)
    val scheduledStore = RedisScheduledJobStore(jobRecordStore, sortedSetAccessor)
    val executor =
      RedisMaintenanceExecutor(
        jobRecordStore = jobRecordStore,
        listAccessor = SpringRedisListAccessor(redisTemplate),
        scheduledJobStore = scheduledStore,
        sortedSetAccessor = sortedSetAccessor,
        leaseStore = newLeaseStore(redisTemplate),
        heartbeatStore = newHeartbeatStore(redisTemplate),
      )

    scheduledStore.persistRetry(samplePayload("job-old"), Instant.parse("2026-03-15T12:00:00Z"))
    scheduledStore.persistRetry(samplePayload("job-new"), Instant.parse("2026-03-15T12:10:00Z"))

    executor.execute(
      MaintenancePlan(
        createdAt = Instant.parse("2026-03-15T12:15:00Z"),
        actions =
          listOf(
            CleanupRetainedBucket(RedisMaintenanceExecutor.BUCKET_RETRY, maxEntries = 1),
          ),
      ),
      now = Instant.parse("2026-03-15T12:15:00Z"),
    )

    val entries = redisTemplate.opsForZSet().range(RedisKeyspace.retry(), 0, -1).orEmpty()
    assertEquals(setOf("job-new"), entries)
    assertNull(jobRecordStore.find(JobId("job-old")))
    assertEquals(JobId("job-new"), jobRecordStore.find(JobId("job-new"))?.jobId)
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
