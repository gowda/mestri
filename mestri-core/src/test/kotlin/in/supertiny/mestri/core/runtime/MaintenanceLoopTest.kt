package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MaintenanceLoopTest {
  @Test
  fun `default maintenance planner detects expired leases and stale workers`() {
    val now = Instant.parse("2026-03-15T12:10:00Z")
    val snapshot =
      MaintenanceSnapshot(
        leases =
          listOf(
            JobLease(
              jobId = JobId("job-expired"),
              workerId = WorkerId("worker-1"),
              acquiredAt = Instant.parse("2026-03-15T12:00:00Z"),
              expiresAt = Instant.parse("2026-03-15T12:05:00Z"),
            ),
            JobLease(
              jobId = JobId("job-live"),
              workerId = WorkerId("worker-2"),
              acquiredAt = Instant.parse("2026-03-15T12:08:00Z"),
              expiresAt = Instant.parse("2026-03-15T12:15:00Z"),
            ),
          ),
        heartbeats =
          listOf(
            WorkerHeartbeat(
              workerId = WorkerId("worker-1"),
              processId = ProcessId("process-1"),
              queues = setOf("default"),
              recordedAt = Instant.parse("2026-03-15T12:00:00Z"),
              expiresAt = Instant.parse("2026-03-15T12:05:00Z"),
            ),
            WorkerHeartbeat(
              workerId = WorkerId("worker-2"),
              processId = ProcessId("process-2"),
              queues = setOf("billing"),
              recordedAt = Instant.parse("2026-03-15T12:09:00Z"),
              expiresAt = Instant.parse("2026-03-15T12:12:00Z"),
            ),
          ),
      )

    val plan = DefaultMaintenancePlanner().plan(snapshot, now)

    assertEquals(
      listOf(
        RequeueExpiredLease(
          jobId = JobId("job-expired"),
          workerId = WorkerId("worker-1"),
        ),
        CleanupStaleWorker(workerId = WorkerId("worker-1")),
      ),
      plan.actions,
    )
  }

  @Test
  fun `maintenance loop snapshots plans and executes in one cycle`() {
    val now = Instant.parse("2026-03-15T12:10:00Z")
    val expectedAction =
      RequeueExpiredLease(
        jobId = JobId("job-expired"),
        workerId = WorkerId("worker-1"),
      )
    var executedPlan: MaintenancePlan? = null
    val loop =
      MaintenanceLoop(
        snapshotSource =
          MaintenanceSnapshotSource {
            MaintenanceSnapshot(
              leases =
                listOf(
                  JobLease(
                    jobId = JobId("job-expired"),
                    workerId = WorkerId("worker-1"),
                    acquiredAt = Instant.parse("2026-03-15T12:00:00Z"),
                    expiresAt = Instant.parse("2026-03-15T12:05:00Z"),
                  ),
                ),
            )
          },
        planner = DefaultMaintenancePlanner(),
        executor =
          MaintenanceExecutor { plan, executedAt ->
            executedPlan = plan
            MaintenanceReport(
              executedAt = executedAt,
              completedActions = plan.actions,
            )
          },
        clock = { now },
      )

    val report = loop.runCycle()

    assertEquals(listOf(expectedAction), executedPlan?.actions)
    assertEquals(listOf(expectedAction), report.completedActions)
    assertEquals(now, report.executedAt)
  }
}
