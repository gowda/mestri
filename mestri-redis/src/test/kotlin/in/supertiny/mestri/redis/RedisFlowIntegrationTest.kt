package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.JobLease
import `in`.supertiny.mestri.core.runtime.ProcessId
import `in`.supertiny.mestri.core.runtime.WorkerHeartbeat
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RedisFlowIntegrationTest {
  @Test
  fun `immediate enqueue writes queue key and queued job record`() {
    val redis = InMemoryRedis()
    val payload = samplePayload(jobId = "job-immediate")
    val recordStore = recordStore(redis)
    val enqueuer =
      RedisImmediateJobEnqueuer(
        jobRecordStore = recordStore,
        listAccessor = redis,
        clock = { Instant.parse("2026-03-15T10:00:00Z") },
      )

    val result = enqueuer.enqueue(payload)
    val record = recordStore.find(payload.jobId)

    assertEquals("mestri:queue:billing", result.queueKey)
    assertEquals(listOf("job-immediate"), redis.listValues("mestri:queue:billing"))
    assertNotNull(record)
    assertEquals(JobBucket.QUEUED, record.bucket)
  }

  @Test
  fun `scheduled and retry persistence write expected zset markers and job buckets`() {
    val redis = InMemoryRedis()
    val recordStore = recordStore(redis)
    val store =
      RedisScheduledJobStore(
        jobRecordStore = recordStore,
        sortedSetAccessor = redis,
        clock = { Instant.parse("2026-03-15T10:00:00Z") },
      )

    val scheduledPayload = samplePayload(jobId = "job-scheduled")
    val retryPayload = samplePayload(jobId = "job-retry")
    store.persistScheduled(scheduledPayload, Instant.parse("2026-03-15T10:05:00Z"))
    store.persistRetry(retryPayload, Instant.parse("2026-03-15T10:10:00Z"))

    assertEquals(
      listOf(
        ScoredValue(
          "job-scheduled",
          Instant.parse("2026-03-15T10:05:00Z").toEpochMilli().toDouble(),
        ),
      ),
      redis.sortedSetValues("mestri:scheduled"),
    )
    assertEquals(
      listOf(
        ScoredValue("job-retry", Instant.parse("2026-03-15T10:10:00Z").toEpochMilli().toDouble()),
      ),
      redis.sortedSetValues("mestri:retry"),
    )
    assertEquals(JobBucket.SCHEDULED, recordStore.find(JobId("job-scheduled"))?.bucket)
    assertEquals(JobBucket.RETRY, recordStore.find(JobId("job-retry"))?.bucket)
  }

  @Test
  fun `lease and heartbeat persistence produce running state inputs`() {
    val redis = InMemoryRedis()
    val leaseStore = RedisJobLeaseStore(redis)
    val heartbeatStore = RedisWorkerHeartbeatStore(redis, redis)

    leaseStore.save(
      JobLease(
        jobId = JobId("job-running"),
        workerId = WorkerId("worker-1"),
        acquiredAt = Instant.parse("2026-03-15T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-15T10:05:00Z"),
      ),
    )
    heartbeatStore.save(
      WorkerHeartbeat(
        workerId = WorkerId("worker-1"),
        processId = ProcessId("process-1"),
        queues = setOf("billing"),
        recordedAt = Instant.parse("2026-03-15T10:00:10Z"),
        expiresAt = Instant.parse("2026-03-15T10:00:40Z"),
      ),
    )

    assertNotNull(leaseStore.find(JobId("job-running")))
    assertNotNull(heartbeatStore.find(WorkerId("worker-1")))
    assertEquals(setOf(ProcessId("process-1")), heartbeatStore.registeredProcessIds())
  }

  @Test
  fun `terminal persistence writes retention markers for succeeded and deleted`() {
    val redis = InMemoryRedis()
    val recordStore = recordStore(redis)
    val terminalStore =
      RedisTerminalJobStore(
        jobRecordStore = recordStore,
        sortedSetAccessor = redis,
      )

    terminalStore.persistSucceeded(
      samplePayload(jobId = "job-done"),
      Instant.parse("2026-03-15T10:15:00Z"),
    )
    terminalStore.persistDeleted(
      samplePayload(jobId = "job-deleted"),
      Instant.parse("2026-03-15T10:20:00Z"),
    )

    assertEquals(
      listOf(
        ScoredValue("job-done", Instant.parse("2026-03-15T10:15:00Z").toEpochMilli().toDouble()),
      ),
      redis.sortedSetValues("mestri:succeeded"),
    )
    assertEquals(
      listOf(
        ScoredValue("job-deleted", Instant.parse("2026-03-15T10:20:00Z").toEpochMilli().toDouble()),
      ),
      redis.sortedSetValues("mestri:deleted"),
    )
    assertEquals(JobBucket.SUCCEEDED, recordStore.find(JobId("job-done"))?.bucket)
    assertEquals(JobBucket.DELETED, recordStore.find(JobId("job-deleted"))?.bucket)
  }

  private fun recordStore(redis: InMemoryRedis): RedisJobRecordStore =
    RedisJobRecordStore(
      hashAccessor = redis,
      mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
    )

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

  private data class ScoredValue(
    val value: String,
    val score: Double,
  )

  private class InMemoryRedis :
    RedisHashAccessor,
    RedisListAccessor,
    RedisSortedSetAccessor,
    RedisSetAccessor {
    private val hashes = mutableMapOf<String, Map<String, String>>()
    private val lists = mutableMapOf<String, MutableList<String>>()
    private val sortedSets = mutableMapOf<String, MutableList<ScoredValue>>()
    private val sets = mutableMapOf<String, MutableSet<String>>()

    override fun entries(key: String): Map<String, String> = hashes[key].orEmpty()

    override fun putAll(
      key: String,
      fields: Map<String, String>,
    ) {
      hashes[key] = fields.toMap()
    }

    override fun delete(key: String) {
      hashes.remove(key)
    }

    override fun rightPush(
      key: String,
      value: String,
    ) {
      lists.getOrPut(key) { mutableListOf() }.add(value)
    }

    override fun add(
      key: String,
      value: String,
      score: Double,
    ) {
      sortedSets.getOrPut(key) { mutableListOf() }.add(ScoredValue(value, score))
    }

    override fun add(
      key: String,
      value: String,
    ) {
      sets.getOrPut(key) { linkedSetOf() }.add(value)
    }

    override fun members(key: String): Set<String> = sets[key].orEmpty()

    fun listValues(key: String): List<String> = lists[key].orEmpty()

    fun sortedSetValues(key: String): List<ScoredValue> = sortedSets[key].orEmpty()
  }
}
