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

class RedisRuntimeStateIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `lease persistence round trips through real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val leaseStore = newLeaseStore(redisTemplate)
    val lease =
      JobLease(
        jobId = JobId("job-running-1"),
        workerId = WorkerId("worker-1"),
        acquiredAt = Instant.parse("2026-03-15T11:00:00Z"),
        expiresAt = Instant.parse("2026-03-15T11:05:00Z"),
      )

    leaseStore.save(lease)

    val loaded = leaseStore.find(lease.jobId)

    assertNotNull(loaded)
    assertEquals(lease, loaded)
  }

  @Test
  fun `heartbeat persistence round trips and registers process in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val heartbeatStore = newHeartbeatStore(redisTemplate)
    val heartbeat =
      WorkerHeartbeat(
        workerId = WorkerId("worker-1"),
        processId = ProcessId("process-1"),
        queues = setOf("default", "critical"),
        recordedAt = Instant.parse("2026-03-15T11:00:10Z"),
        expiresAt = Instant.parse("2026-03-15T11:00:40Z"),
      )

    heartbeatStore.save(heartbeat)

    val loaded = heartbeatStore.find(heartbeat.workerId)
    val registeredProcesses = heartbeatStore.registeredProcessIds()

    assertNotNull(loaded)
    assertEquals(heartbeat, loaded)
    assertEquals(setOf(ProcessId("process-1")), registeredProcesses)
  }

  @Test
  fun `heartbeat delete removes worker and process registry entries in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val heartbeatStore = newHeartbeatStore(redisTemplate)
    val heartbeat =
      WorkerHeartbeat(
        workerId = WorkerId("worker-1"),
        processId = ProcessId("process-1"),
        queues = setOf("default"),
        recordedAt = Instant.parse("2026-03-15T11:00:10Z"),
        expiresAt = Instant.parse("2026-03-15T11:00:40Z"),
      )

    heartbeatStore.save(heartbeat)
    heartbeatStore.delete(heartbeat.workerId)

    assertNull(heartbeatStore.find(heartbeat.workerId))
    assertEquals(emptySet(), heartbeatStore.registeredWorkerIds())
    assertEquals(emptySet(), heartbeatStore.registeredProcessIds())
  }
}
