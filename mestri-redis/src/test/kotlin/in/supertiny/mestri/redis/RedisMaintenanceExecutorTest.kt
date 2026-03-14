package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.CleanupStaleWorker
import `in`.supertiny.mestri.core.runtime.MaintenancePlan
import `in`.supertiny.mestri.core.runtime.RequeueExpiredLease
import `in`.supertiny.mestri.core.runtime.RetryBackoffCalculator
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RedisMaintenanceExecutorTest {
  @Test
  fun `expired running job is moved to retry when attempts remain`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val setAccessor = InMemoryRedisSetAccessor()
    val listAccessor = InMemoryRedisListAccessor()
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val jobRecordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val leaseStore = RedisJobLeaseStore(hashAccessor, setAccessor)
    val executor =
      RedisMaintenanceExecutor(
        jobRecordStore = jobRecordStore,
        listAccessor = listAccessor,
        scheduledJobStore = RedisScheduledJobStore(jobRecordStore, sortedSetAccessor),
        leaseStore = leaseStore,
        backoffCalculator = RetryBackoffCalculator(),
      )
    val payload = samplePayload()
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
    leaseStore.save(sampleLease(payload.jobId))

    val report =
      executor.execute(
        MaintenancePlan(
          createdAt = now,
          actions = listOf(RequeueExpiredLease(payload.jobId, WorkerId("worker-1"))),
        ),
        now = now,
      )

    assertEquals(1, report.completedActions.size)
    assertEquals(JobBucket.RETRY, jobRecordStore.find(payload.jobId)?.bucket)
    assertEquals(
      "2",
      jobRecordStore.find(payload.jobId)?.payload?.metadata?.get(RedisRetryExecutionHandler.METADATA_NEXT_ATTEMPT),
    )
    assertNull(leaseStore.find(payload.jobId))
    assertEquals(emptyList(), listAccessor.valuesFor(RedisKeyspace.queue(payload.queue)))
    assertEquals(
      listOf(ScoredValue(payload.jobId.value, Instant.parse("2026-03-15T12:10:05Z").toEpochMilli().toDouble())),
      sortedSetAccessor.valuesFor(RedisKeyspace.retry()),
    )
  }

  @Test
  fun `expired running job is requeued when retries are exhausted or disabled`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val setAccessor = InMemoryRedisSetAccessor()
    val listAccessor = InMemoryRedisListAccessor()
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val jobRecordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val leaseStore = RedisJobLeaseStore(hashAccessor, setAccessor)
    val executor =
      RedisMaintenanceExecutor(
        jobRecordStore = jobRecordStore,
        listAccessor = listAccessor,
        scheduledJobStore = RedisScheduledJobStore(jobRecordStore, sortedSetAccessor),
        leaseStore = leaseStore,
      )
    val payload =
      samplePayload(
        retryPolicy = RetryPolicy.NONE,
        metadata = mapOf(RedisRetryExecutionHandler.METADATA_NEXT_ATTEMPT to "1"),
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
    leaseStore.save(sampleLease(payload.jobId))

    val report =
      executor.execute(
        MaintenancePlan(
          createdAt = now,
          actions =
            listOf(
              RequeueExpiredLease(payload.jobId, WorkerId("worker-1")),
              CleanupStaleWorker(WorkerId("worker-2")),
            ),
        ),
        now = now,
      )

    assertEquals(1, report.completedActions.size)
    assertEquals(1, report.skippedActions.size)
    assertEquals(JobBucket.QUEUED, jobRecordStore.find(payload.jobId)?.bucket)
    assertEquals(
      listOf(payload.jobId.value),
      listAccessor.valuesFor(RedisKeyspace.queue(payload.queue)),
    )
    assertEquals(emptyList(), sortedSetAccessor.valuesFor(RedisKeyspace.retry()))
    assertNull(leaseStore.find(payload.jobId))
  }

  private fun samplePayload(
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
      retryPolicy = retryPolicy,
      metadata = metadata,
    )

  private fun sampleLease(jobId: JobId) =
    `in`.supertiny.mestri.core.runtime.JobLease(
      jobId = jobId,
      workerId = WorkerId("worker-1"),
      acquiredAt = Instant.parse("2026-03-15T12:01:00Z"),
      expiresAt = Instant.parse("2026-03-15T12:06:00Z"),
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

  private class InMemoryRedisSetAccessor : RedisSetAccessor {
    private val data = mutableMapOf<String, MutableSet<String>>()

    override fun add(key: String, value: String) {
      data.getOrPut(key) { linkedSetOf() }.add(value)
    }

    override fun members(key: String): Set<String> = data[key].orEmpty()

    override fun remove(key: String, value: String) {
      data[key]?.remove(value)
    }
  }

  private class InMemoryRedisListAccessor : RedisListAccessor {
    private val data = mutableMapOf<String, MutableList<String>>()

    override fun rightPush(key: String, value: String) {
      data.getOrPut(key) { mutableListOf() }.add(value)
    }

    fun valuesFor(key: String): List<String> = data[key].orEmpty()
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
