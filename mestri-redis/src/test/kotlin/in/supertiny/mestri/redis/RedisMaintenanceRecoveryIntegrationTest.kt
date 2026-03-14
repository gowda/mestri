package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.DefaultMaintenancePlanner
import `in`.supertiny.mestri.core.runtime.JobLease
import `in`.supertiny.mestri.core.runtime.MaintenanceLoop
import `in`.supertiny.mestri.core.runtime.ProcessId
import `in`.supertiny.mestri.core.runtime.WorkerHeartbeat
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedisMaintenanceRecoveryIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `maintenance loop recovers expired lease from crashed worker into retry and cleans worker`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)
    val jobRecordStore = newJobRecordStore(redisTemplate)
    val leaseStore = newLeaseStore(redisTemplate)
    val heartbeatStore = newHeartbeatStore(redisTemplate)
    val sortedSetAccessor = newSortedSetAccessor(redisTemplate)
    val now = Instant.parse("2026-03-15T12:10:00Z")
    val payload = samplePayload(jobId = "job-crashed-retry")
    val workerId = WorkerId("worker-crashed-1")
    val processId = ProcessId("process-crashed-1")

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
      JobLease(
        jobId = payload.jobId,
        workerId = workerId,
        acquiredAt = Instant.parse("2026-03-15T12:01:00Z"),
        expiresAt = Instant.parse("2026-03-15T12:06:00Z"),
      ),
    )
    heartbeatStore.save(
      WorkerHeartbeat(
        workerId = workerId,
        processId = processId,
        queues = setOf(payload.queue.value),
        recordedAt = Instant.parse("2026-03-15T12:00:00Z"),
        expiresAt = Instant.parse("2026-03-15T12:05:00Z"),
      ),
    )

    val loop =
      MaintenanceLoop(
        snapshotSource = RedisMaintenanceSnapshotSource(leaseStore, heartbeatStore),
        planner = DefaultMaintenancePlanner(),
        executor =
          RedisMaintenanceExecutor(
            jobRecordStore = jobRecordStore,
            listAccessor = SpringRedisListAccessor(redisTemplate),
            scheduledJobStore = RedisScheduledJobStore(jobRecordStore, sortedSetAccessor),
            sortedSetAccessor = sortedSetAccessor,
            leaseStore = leaseStore,
            heartbeatStore = heartbeatStore,
          ),
        clock = { now },
      )

    val report = loop.runCycle()
    val record = jobRecordStore.find(payload.jobId)

    assertEquals(2, report.completedActions.size)
    assertEquals(JobBucket.RETRY, record?.bucket)
    assertEquals(
      "2",
      record?.payload?.metadata?.get(RedisRetryExecutionHandler.METADATA_NEXT_ATTEMPT),
    )
    assertEquals(
      RedisMaintenanceExecutor.METADATA_ORPHAN_ERROR_MESSAGE,
      record?.payload?.metadata?.get(RedisRetryExecutionHandler.METADATA_LAST_ERROR_MESSAGE),
    )
    assertNull(leaseStore.find(payload.jobId))
    assertNull(heartbeatStore.find(workerId))
    assertEquals(emptySet(), heartbeatStore.registeredWorkerIds())
    assertEquals(emptySet(), heartbeatStore.registeredProcessIds())
  }

  @Test
  fun `maintenance loop requeues expired lease when retries are exhausted`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)
    val jobRecordStore = newJobRecordStore(redisTemplate)
    val leaseStore = newLeaseStore(redisTemplate)
    val heartbeatStore = newHeartbeatStore(redisTemplate)
    val sortedSetAccessor = newSortedSetAccessor(redisTemplate)
    val now = Instant.parse("2026-03-15T12:10:00Z")
    val payload =
      samplePayload(
        jobId = "job-crashed-queue",
        retryPolicy = RetryPolicy.NONE,
        metadata = mapOf(RedisRetryExecutionHandler.METADATA_NEXT_ATTEMPT to "1"),
      )
    val workerId = WorkerId("worker-crashed-2")

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
      JobLease(
        jobId = payload.jobId,
        workerId = workerId,
        acquiredAt = Instant.parse("2026-03-15T12:01:00Z"),
        expiresAt = Instant.parse("2026-03-15T12:06:00Z"),
      ),
    )

    val loop =
      MaintenanceLoop(
        snapshotSource = RedisMaintenanceSnapshotSource(leaseStore, heartbeatStore),
        planner = DefaultMaintenancePlanner(),
        executor =
          RedisMaintenanceExecutor(
            jobRecordStore = jobRecordStore,
            listAccessor = SpringRedisListAccessor(redisTemplate),
            scheduledJobStore = RedisScheduledJobStore(jobRecordStore, sortedSetAccessor),
            sortedSetAccessor = sortedSetAccessor,
            leaseStore = leaseStore,
            heartbeatStore = heartbeatStore,
          ),
        clock = { now },
      )

    val report = loop.runCycle()
    val record = jobRecordStore.find(payload.jobId)
    val queueEntries =
      redisTemplate.opsForList().range(RedisKeyspace.queue(payload.queue), 0, -1).orEmpty()

    assertEquals(1, report.completedActions.size)
    assertEquals(JobBucket.QUEUED, record?.bucket)
    assertEquals(listOf(payload.jobId.value), queueEntries)
    assertNull(leaseStore.find(payload.jobId))
  }

  private fun samplePayload(
    jobId: String,
    retryPolicy: RetryPolicy =
      RetryPolicy(
        maxAttempts = 5,
        initialDelay = 5.seconds,
        maxDelay = 2.minutes,
        backoffMultiplier = 2.0,
        useJitter = false,
      ),
    metadata: Map<String, String> = emptyMap(),
  ): JobPayload =
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
      retryPolicy = retryPolicy,
      metadata = metadata,
    )
}
