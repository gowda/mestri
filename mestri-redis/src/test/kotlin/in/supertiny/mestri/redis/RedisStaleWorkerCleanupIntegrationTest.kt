package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.runtime.CleanupStaleWorker
import `in`.supertiny.mestri.core.runtime.MaintenancePlan
import `in`.supertiny.mestri.core.runtime.ProcessId
import `in`.supertiny.mestri.core.runtime.WorkerHeartbeat
import `in`.supertiny.mestri.core.runtime.WorkerId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RedisStaleWorkerCleanupIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `stale worker cleanup removes heartbeat and worker registry entries in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val hashAccessor = newHashAccessor(redisTemplate)
    val setAccessor = newSetAccessor(redisTemplate)
    val heartbeatStore = RedisWorkerHeartbeatStore(hashAccessor, setAccessor)
    val executor =
      RedisMaintenanceExecutor(
        jobRecordStore = newJobRecordStore(redisTemplate),
        listAccessor = SpringRedisListAccessor(redisTemplate),
        scheduledJobStore = RedisScheduledJobStore(newJobRecordStore(redisTemplate), newSortedSetAccessor(redisTemplate)),
        leaseStore = newLeaseStore(redisTemplate),
        heartbeatStore = heartbeatStore,
      )
    val heartbeat =
      WorkerHeartbeat(
        workerId = WorkerId("worker-1"),
        processId = ProcessId("process-1"),
        queues = setOf("billing"),
        recordedAt = Instant.parse("2026-03-15T12:00:00Z"),
        expiresAt = Instant.parse("2026-03-15T12:05:00Z"),
      )

    heartbeatStore.save(heartbeat)

    executor.execute(
      MaintenancePlan(
        createdAt = Instant.parse("2026-03-15T12:10:00Z"),
        actions = listOf(CleanupStaleWorker(heartbeat.workerId)),
      ),
      now = Instant.parse("2026-03-15T12:10:00Z"),
    )

    assertNull(heartbeatStore.find(heartbeat.workerId))
    assertEquals(emptySet(), heartbeatStore.registeredWorkerIds())
    assertEquals(emptySet(), heartbeatStore.registeredProcessIds())
  }
}
