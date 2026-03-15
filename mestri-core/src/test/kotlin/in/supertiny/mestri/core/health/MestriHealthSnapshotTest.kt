package `in`.supertiny.mestri.core.health

import `in`.supertiny.mestri.core.model.QueueName
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MestriHealthSnapshotTest {
  @Test
  fun `health snapshot captures redis worker queue and scheduler signals`() {
    val checkedAt = Instant.parse("2026-03-15T12:00:00Z")

    val snapshot =
      MestriHealthSnapshot(
        status = MestriHealthStatus.DEGRADED,
        checkedAt = checkedAt,
        redis =
          RedisHealth(
            reachable = true,
            checkedAt = checkedAt,
          ),
        workers =
          WorkerHealth(
            activeWorkers = 2,
            staleWorkers = 1,
            staleLeases = 3,
          ),
        queues =
          listOf(
            QueueDepth(
              queue = QueueName("default"),
              enqueuedJobs = 12,
              oldestJobAge = Duration.ofMinutes(4),
            ),
          ),
        buckets =
          JobBucketDepths(
            scheduledJobs = 5,
            retryJobs = 2,
            deadJobs = 1,
            succeededJobs = 40,
            deletedJobs = 7,
          ),
        scheduler =
          SchedulerHealth(
            schedulerEnabled = true,
            promoterEnabled = true,
            checkedAt = checkedAt,
          ),
        details = mapOf("reason" to "stale leases exceeded threshold"),
      )

    assertEquals(MestriHealthStatus.DEGRADED, snapshot.status)
    assertEquals(2, snapshot.workers.activeWorkers)
    assertEquals(12, snapshot.queues.single().enqueuedJobs)
    assertEquals(2, snapshot.buckets.retryJobs)
    assertEquals(true, snapshot.redis.reachable)
  }

  @Test
  fun `health snapshot rejects blank detail keys`() {
    val checkedAt = Instant.parse("2026-03-15T12:00:00Z")

    assertFailsWith<IllegalArgumentException> {
      MestriHealthSnapshot(
        status = MestriHealthStatus.UP,
        checkedAt = checkedAt,
        redis = RedisHealth(reachable = true, checkedAt = checkedAt),
        workers = WorkerHealth(activeWorkers = 1),
        scheduler =
          SchedulerHealth(
            schedulerEnabled = true,
            promoterEnabled = true,
            checkedAt = checkedAt,
          ),
        details = mapOf("" to "bad"),
      )
    }
  }

  @Test
  fun `queue depth rejects negative age`() {
    assertFailsWith<IllegalArgumentException> {
      QueueDepth(
        queue = QueueName("default"),
        enqueuedJobs = 1,
        oldestJobAge = Duration.ofSeconds(-1),
      )
    }
  }
}
