package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.MaintenancePlan
import `in`.supertiny.mestri.core.runtime.RequeueExpiredLease
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedisMaintenanceExecutorIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `maintenance executor moves orphaned running job to retry in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)
    val jobRecordStore = newJobRecordStore(redisTemplate)
    val leaseStore = newLeaseStore(redisTemplate)
    val payload = samplePayload()
    val executor =
      RedisMaintenanceExecutor(
        jobRecordStore = jobRecordStore,
        listAccessor = SpringRedisListAccessor(redisTemplate),
        scheduledJobStore = RedisScheduledJobStore(jobRecordStore, newSortedSetAccessor(redisTemplate)),
        leaseStore = leaseStore,
      )
    val now = Instant.parse("2026-03-15T12:10:00Z")

    jobRecordStore.save(
      RedisJobRecord(
        jobId = payload.jobId,
        bucket = JobBucket.RUNNING,
        payload = payload,
        createdAt = Instant.parse("2026-03-15T12:00:00Z"),
        updatedAt = Instant.parse("2026-03-15T12:01:00Z"),
      ),
    )
    leaseStore.save(
      `in`.supertiny.mestri.core.runtime.JobLease(
        jobId = payload.jobId,
        workerId = WorkerId("worker-1"),
        acquiredAt = Instant.parse("2026-03-15T12:01:00Z"),
        expiresAt = Instant.parse("2026-03-15T12:06:00Z"),
      ),
    )

    executor.execute(
      MaintenancePlan(
        createdAt = now,
        actions = listOf(RequeueExpiredLease(payload.jobId, WorkerId("worker-1"))),
      ),
      now = now,
    )

    val record = jobRecordStore.find(payload.jobId)
    val retryScore = redisTemplate.opsForZSet().score(RedisKeyspace.retry(), payload.jobId.value)

    assertEquals(JobBucket.RETRY, record?.bucket)
    assertEquals("2", record?.payload?.metadata?.get(RedisRetryExecutionHandler.METADATA_NEXT_ATTEMPT))
    assertEquals(Instant.parse("2026-03-15T12:10:05Z").toEpochMilli().toDouble(), retryScore)
    assertNull(leaseStore.find(payload.jobId))
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
      retryPolicy =
        RetryPolicy(
          maxAttempts = 5,
          initialDelay = 5.seconds,
          maxDelay = 2.minutes,
          backoffMultiplier = 2.0,
          useJitter = false,
        ),
    )
}
