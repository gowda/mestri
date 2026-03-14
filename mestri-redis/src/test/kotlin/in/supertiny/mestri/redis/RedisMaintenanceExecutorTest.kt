package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.CleanupRetainedBucket
import `in`.supertiny.mestri.core.runtime.CleanupStaleWorker
import `in`.supertiny.mestri.core.runtime.MaintenancePlan
import `in`.supertiny.mestri.core.runtime.RequeueExpiredLease
import `in`.supertiny.mestri.core.runtime.RetryBackoffCalculator
import `in`.supertiny.mestri.core.runtime.WorkerHeartbeat
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        sortedSetAccessor = sortedSetAccessor,
        leaseStore = leaseStore,
        heartbeatStore = RedisWorkerHeartbeatStore(hashAccessor, setAccessor),
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
      jobRecordStore
        .find(
          payload.jobId,
        )?.payload
        ?.metadata
        ?.get(RedisRetryExecutionHandler.METADATA_NEXT_ATTEMPT),
    )
    assertNull(leaseStore.find(payload.jobId))
    assertEquals(emptyList(), listAccessor.valuesFor(RedisKeyspace.queue(payload.queue)))
    assertEquals(
      listOf(
        ScoredValue(
          payload.jobId.value,
          Instant.parse("2026-03-15T12:10:05Z").toEpochMilli().toDouble(),
        ),
      ),
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
        sortedSetAccessor = sortedSetAccessor,
        leaseStore = leaseStore,
        heartbeatStore = RedisWorkerHeartbeatStore(hashAccessor, setAccessor),
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

  @Test
  fun `stale worker cleanup removes heartbeat and registry entries`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val setAccessor = InMemoryRedisSetAccessor()
    val heartbeatStore = RedisWorkerHeartbeatStore(hashAccessor, setAccessor)
    val executor =
      RedisMaintenanceExecutor(
        jobRecordStore =
          RedisJobRecordStore(
            hashAccessor = hashAccessor,
            mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
          ),
        listAccessor = InMemoryRedisListAccessor(),
        scheduledJobStore =
          RedisScheduledJobStore(
            RedisJobRecordStore(
              hashAccessor = hashAccessor,
              mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
            ),
            InMemoryRedisSortedSetAccessor(),
          ),
        sortedSetAccessor = InMemoryRedisSortedSetAccessor(),
        leaseStore = RedisJobLeaseStore(hashAccessor, setAccessor),
        heartbeatStore = heartbeatStore,
      )
    val heartbeat =
      WorkerHeartbeat(
        workerId = WorkerId("worker-1"),
        processId =
          `in`.supertiny.mestri.core.runtime
            .ProcessId("process-1"),
        queues = setOf("billing"),
        recordedAt = Instant.parse("2026-03-15T12:00:00Z"),
        expiresAt = Instant.parse("2026-03-15T12:05:00Z"),
      )

    heartbeatStore.save(heartbeat)
    assertNotNull(heartbeatStore.find(heartbeat.workerId))

    val report =
      executor.execute(
        MaintenancePlan(
          createdAt = Instant.parse("2026-03-15T12:10:00Z"),
          actions = listOf(CleanupStaleWorker(heartbeat.workerId)),
        ),
        now = Instant.parse("2026-03-15T12:10:00Z"),
      )

    assertEquals(listOf(CleanupStaleWorker(WorkerId("worker-1"))), report.completedActions)
    assertNull(heartbeatStore.find(heartbeat.workerId))
    assertEquals(emptySet(), heartbeatStore.registeredWorkerIds())
    assertEquals(emptySet(), heartbeatStore.registeredProcessIds())
  }

  @Test
  fun `retention cleanup trims succeeded bucket and deletes old records`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val setAccessor = InMemoryRedisSetAccessor()
    val listAccessor = InMemoryRedisListAccessor()
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val jobRecordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val terminalStore = RedisTerminalJobStore(jobRecordStore, sortedSetAccessor)
    val executor =
      RedisMaintenanceExecutor(
        jobRecordStore = jobRecordStore,
        listAccessor = listAccessor,
        scheduledJobStore = RedisScheduledJobStore(jobRecordStore, sortedSetAccessor),
        sortedSetAccessor = sortedSetAccessor,
        leaseStore = RedisJobLeaseStore(hashAccessor, setAccessor),
        heartbeatStore = RedisWorkerHeartbeatStore(hashAccessor, setAccessor),
      )
    val oldPayload =
      samplePayload(
        metadata = mapOf("marker" to "old"),
      ).copy(jobId = JobId("job-old"))
    val newPayload =
      samplePayload(
        metadata = mapOf("marker" to "new"),
      ).copy(jobId = JobId("job-new"))

    terminalStore.persistSucceeded(oldPayload, Instant.parse("2026-03-15T12:00:00Z"))
    terminalStore.persistSucceeded(newPayload, Instant.parse("2026-03-15T12:10:00Z"))

    val report =
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

    assertEquals(1, report.completedActions.size)
    assertEquals(null, jobRecordStore.find(JobId("job-old")))
    assertEquals(JobId("job-new"), jobRecordStore.find(JobId("job-new"))?.jobId)
    assertEquals(
      listOf("job-new"),
      sortedSetAccessor.valuesFor(RedisKeyspace.succeeded()).map {
        it.value
      },
    )
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

    override fun putAll(
      key: String,
      fields: Map<String, String>,
    ) {
      data[key] = fields.toMap()
    }

    override fun delete(key: String) {
      data.remove(key)
    }
  }

  private class InMemoryRedisSetAccessor : RedisSetAccessor {
    private val data = mutableMapOf<String, MutableSet<String>>()

    override fun add(
      key: String,
      value: String,
    ) {
      data.getOrPut(key) { linkedSetOf() }.add(value)
    }

    override fun members(key: String): Set<String> = data[key].orEmpty()

    override fun remove(
      key: String,
      value: String,
    ) {
      data[key]?.remove(value)
    }
  }

  private class InMemoryRedisListAccessor : RedisListAccessor {
    private val data = mutableMapOf<String, MutableList<String>>()

    override fun rightPush(
      key: String,
      value: String,
    ) {
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

    override fun add(
      key: String,
      value: String,
      score: Double,
    ) {
      data.getOrPut(key) { mutableListOf() }.add(ScoredValue(value, score))
    }

    override fun range(
      key: String,
      start: Long,
      end: Long,
    ): List<String> {
      val values = data[key].orEmpty().sortedBy { it.score }
      if (values.isEmpty()) return emptyList()
      val normalizedEnd = if (end < 0) values.lastIndex else minOf(end.toInt(), values.lastIndex)
      if (start > normalizedEnd) return emptyList()
      return values.subList(start.toInt(), normalizedEnd + 1).map { it.value }
    }

    override fun remove(
      key: String,
      value: String,
    ) {
      data[key]?.removeAll { it.value == value }
    }

    fun valuesFor(key: String): List<ScoredValue> = data[key].orEmpty()
  }
}
