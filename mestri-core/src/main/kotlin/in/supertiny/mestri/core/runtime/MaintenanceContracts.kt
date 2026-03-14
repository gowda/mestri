package `in`.supertiny.mestri.core.runtime

import java.time.Instant

data class MaintenanceSnapshot(
  val leases: List<JobLease> = emptyList(),
  val heartbeats: List<WorkerHeartbeat> = emptyList(),
)

data class MaintenancePlan(
  val createdAt: Instant,
  val actions: List<MaintenanceAction> = emptyList(),
)

data class MaintenanceReport(
  val executedAt: Instant,
  val completedActions: List<MaintenanceAction> = emptyList(),
  val skippedActions: List<MaintenanceAction> = emptyList(),
)

fun interface MaintenanceSnapshotSource {
  fun snapshot(now: Instant): MaintenanceSnapshot
}

fun interface MaintenancePlanner {
  fun plan(
    snapshot: MaintenanceSnapshot,
    now: Instant,
  ): MaintenancePlan
}

fun interface MaintenanceExecutor {
  fun execute(
    plan: MaintenancePlan,
    now: Instant,
  ): MaintenanceReport
}

class DefaultMaintenancePlanner : MaintenancePlanner {
  override fun plan(
    snapshot: MaintenanceSnapshot,
    now: Instant,
  ): MaintenancePlan {
    val expiredLeaseActions =
      snapshot.leases
        .filter { it.isExpired(at = now) }
        .map { lease ->
          RequeueExpiredLease(
            jobId = lease.jobId,
            workerId = lease.workerId,
          )
        }
    val staleWorkerActions =
      snapshot.heartbeats
        .filter { it.isStale(at = now) }
        .distinctBy { it.workerId }
        .map { heartbeat -> CleanupStaleWorker(workerId = heartbeat.workerId) }

    return MaintenancePlan(
      createdAt = now,
      actions = expiredLeaseActions + staleWorkerActions,
    )
  }
}

class MaintenanceLoop(
  private val snapshotSource: MaintenanceSnapshotSource,
  private val planner: MaintenancePlanner,
  private val executor: MaintenanceExecutor,
  private val clock: () -> Instant = { Instant.now() },
) {
  fun runCycle(): MaintenanceReport {
    val now = clock()
    val snapshot = snapshotSource.snapshot(now)
    val plan = planner.plan(snapshot, now)
    return executor.execute(plan, now)
  }
}
