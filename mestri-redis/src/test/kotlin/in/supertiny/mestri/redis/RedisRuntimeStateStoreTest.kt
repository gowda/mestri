package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.runtime.JobLease
import `in`.supertiny.mestri.core.runtime.ProcessId
import `in`.supertiny.mestri.core.runtime.WorkerHeartbeat
import `in`.supertiny.mestri.core.runtime.WorkerId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RedisRuntimeStateStoreTest {
  @Test
  fun `save and find lease round trips lease fields`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val store = RedisJobLeaseStore(hashAccessor)
    val lease =
      JobLease(
        jobId = JobId("job-123"),
        workerId = WorkerId("worker-1"),
        acquiredAt = Instant.parse("2026-03-15T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-15T10:05:00Z"),
      )

    store.save(lease)
    val loaded = store.find(lease.jobId)

    assertNotNull(loaded)
    assertEquals(lease, loaded)
  }

  @Test
  fun `find lease returns null when missing`() {
    val store = RedisJobLeaseStore(InMemoryRedisHashAccessor())

    assertNull(store.find(JobId("missing")))
  }

  @Test
  fun `delete lease removes stored lease`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val store = RedisJobLeaseStore(hashAccessor)
    val lease =
      JobLease(
        jobId = JobId("job-123"),
        workerId = WorkerId("worker-1"),
        acquiredAt = Instant.parse("2026-03-15T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-15T10:05:00Z"),
      )

    store.save(lease)

    store.delete(lease.jobId)

    assertNull(store.find(lease.jobId))
  }

  @Test
  fun `save heartbeat persists heartbeat and registers process`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val setAccessor = InMemoryRedisSetAccessor()
    val store = RedisWorkerHeartbeatStore(hashAccessor, setAccessor)
    val heartbeat =
      WorkerHeartbeat(
        workerId = WorkerId("worker-1"),
        processId = ProcessId("process-1"),
        queues = setOf("default", "critical"),
        recordedAt = Instant.parse("2026-03-15T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-15T10:00:30Z"),
      )

    store.save(heartbeat)
    val loaded = store.find(heartbeat.workerId)

    assertNotNull(loaded)
    assertEquals(heartbeat, loaded)
    assertEquals(setOf(ProcessId("process-1")), store.registeredProcessIds())
  }

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
  }
}
