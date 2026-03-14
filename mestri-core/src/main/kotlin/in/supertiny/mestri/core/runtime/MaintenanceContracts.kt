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

fun interface MaintenancePlanner {
  fun plan(snapshot: MaintenanceSnapshot, now: Instant): MaintenancePlan
}

fun interface MaintenanceExecutor {
  fun execute(plan: MaintenancePlan, now: Instant): MaintenanceReport
}
