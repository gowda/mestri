package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.runtime.JobLease
import `in`.supertiny.mestri.core.runtime.ProcessId
import `in`.supertiny.mestri.core.runtime.WorkerHeartbeat
import `in`.supertiny.mestri.core.runtime.WorkerId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RedisMaintenanceSnapshotSourceTest {
  @Test
  fun `maintenance snapshot source reads leases and heartbeats from redis stores`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val setAccessor = InMemoryRedisSetAccessor()
    val leaseStore = RedisJobLeaseStore(hashAccessor, setAccessor)
    val heartbeatStore = RedisWorkerHeartbeatStore(hashAccessor, setAccessor)
    val source = RedisMaintenanceSnapshotSource(leaseStore, heartbeatStore)
    val lease =
      JobLease(
        jobId = JobId("job-123"),
        workerId = WorkerId("worker-1"),
        acquiredAt = Instant.parse("2026-03-15T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-15T10:05:00Z"),
      )
    val heartbeat =
      WorkerHeartbeat(
        workerId = WorkerId("worker-1"),
        processId = ProcessId("process-1"),
        queues = setOf("default"),
        recordedAt = Instant.parse("2026-03-15T10:00:00Z"),
        expiresAt = Instant.parse("2026-03-15T10:00:30Z"),
      )

    leaseStore.save(lease)
    heartbeatStore.save(heartbeat)

    val snapshot = source.snapshot(Instant.parse("2026-03-15T10:10:00Z"))

    assertEquals(listOf(lease), snapshot.leases)
    assertEquals(listOf(heartbeat), snapshot.heartbeats)
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

    override fun remove(
      key: String,
      value: String,
    ) {
      data[key]?.remove(value)
    }
  }
}
