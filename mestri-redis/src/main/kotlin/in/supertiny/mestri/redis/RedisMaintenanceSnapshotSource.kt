package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.runtime.MaintenanceSnapshot
import `in`.supertiny.mestri.core.runtime.MaintenanceSnapshotSource
import java.time.Instant

class RedisMaintenanceSnapshotSource(
  private val leaseStore: RedisJobLeaseStore,
  private val heartbeatStore: RedisWorkerHeartbeatStore,
) : MaintenanceSnapshotSource {
  override fun snapshot(now: Instant): MaintenanceSnapshot =
    MaintenanceSnapshot(
      leases = leaseStore.findAll(),
      heartbeats = heartbeatStore.findAll(),
    )
}
