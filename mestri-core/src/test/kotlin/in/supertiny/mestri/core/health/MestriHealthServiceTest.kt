package `in`.supertiny.mestri.core.health

import `in`.supertiny.mestri.core.model.QueueName
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MestriHealthServiceTest {
  @Test
  fun `health service builds snapshot from observation source`() {
    val checkedAt = Instant.parse("2026-03-15T12:00:00Z")
    val service =
      MestriHealthService(
        observationSource = { now ->
          MestriHealthObservation(
            checkedAt = now,
            redis = RedisHealth(reachable = true, checkedAt = now),
            workers = WorkerHealth(activeWorkers = 2, staleWorkers = 1, staleLeases = 3),
            queues =
              listOf(
                QueueDepth(
                  queue = QueueName("default"),
                  enqueuedJobs = 5,
                  oldestJobAge = Duration.ofMinutes(2),
                ),
              ),
            buckets = JobBucketDepths(retryJobs = 4, deadJobs = 1),
            scheduler =
              SchedulerHealth(
                schedulerEnabled = true,
                promoterEnabled = true,
                checkedAt = now,
              ),
            details = mapOf("source" to "standalone"),
          )
        },
        clock = { checkedAt },
      )

    val snapshot = service.health()

    assertEquals(MestriHealthStatus.UP, snapshot.status)
    assertEquals(checkedAt, snapshot.checkedAt)
    assertEquals(2, snapshot.workers.activeWorkers)
    assertEquals(5, snapshot.queues.single().enqueuedJobs)
    assertEquals(4, snapshot.buckets.retryJobs)
    assertEquals("standalone", snapshot.details["source"])
  }

  @Test
  fun `health service uses custom status evaluator`() {
    val checkedAt = Instant.parse("2026-03-15T12:00:00Z")
    val service =
      MestriHealthService(
        observationSource = { now ->
          MestriHealthObservation(
            checkedAt = now,
            redis = RedisHealth(reachable = true, checkedAt = now),
            workers = WorkerHealth(activeWorkers = 1),
            scheduler =
              SchedulerHealth(
                schedulerEnabled = true,
                promoterEnabled = true,
                checkedAt = now,
              ),
          )
        },
        statusEvaluator =
          {
            MestriHealthAssessment(
              status = MestriHealthStatus.DEGRADED,
              details = mapOf("reason" to "custom"),
            )
          },
        clock = { checkedAt },
      )

    val snapshot = service.health()

    assertEquals(MestriHealthStatus.DEGRADED, snapshot.status)
    assertEquals("custom", snapshot.details["reason"])
  }

  @Test
  fun `default status evaluator marks unreachable redis as down`() {
    val checkedAt = Instant.parse("2026-03-15T12:00:00Z")
    val service =
      MestriHealthService(
        observationSource = { now ->
          MestriHealthObservation(
            checkedAt = now,
            redis =
              RedisHealth(
                reachable = false,
                checkedAt = now,
                errorMessage = "timeout",
              ),
            workers = WorkerHealth(activeWorkers = 0),
            scheduler =
              SchedulerHealth(
                schedulerEnabled = false,
                promoterEnabled = false,
                checkedAt = now,
              ),
          )
        },
        clock = { checkedAt },
      )

    val snapshot = service.health()

    assertEquals(MestriHealthStatus.DOWN, snapshot.status)
    assertEquals("redis unreachable", snapshot.details["reason"])
  }

  @Test
  fun `threshold evaluator degrades on queue pressure`() {
    val checkedAt = Instant.parse("2026-03-15T12:00:00Z")
    val service =
      MestriHealthService(
        observationSource = { now ->
          MestriHealthObservation(
            checkedAt = now,
            redis = RedisHealth(reachable = true, checkedAt = now),
            workers = WorkerHealth(activeWorkers = 2),
            queues =
              listOf(
                QueueDepth(
                  queue = QueueName("default"),
                  enqueuedJobs = 6,
                ),
              ),
            scheduler =
              SchedulerHealth(
                schedulerEnabled = true,
                promoterEnabled = true,
                checkedAt = now,
              ),
          )
        },
        statusEvaluator =
          ThresholdBasedMestriHealthStatusEvaluator(
            ThresholdBasedMestriHealthThresholds(maxEnqueuedJobs = 5),
          ),
        clock = { checkedAt },
      )

    val snapshot = service.health()

    assertEquals(MestriHealthStatus.DEGRADED, snapshot.status)
    assertEquals("enqueued job threshold exceeded", snapshot.details["reason"])
  }

  @Test
  fun `threshold evaluator marks dead jobs or no active workers as down`() {
    val checkedAt = Instant.parse("2026-03-15T12:00:00Z")
    val service =
      MestriHealthService(
        observationSource = { now ->
          MestriHealthObservation(
            checkedAt = now,
            redis = RedisHealth(reachable = true, checkedAt = now),
            workers = WorkerHealth(activeWorkers = 0),
            queues =
              listOf(
                QueueDepth(
                  queue = QueueName("default"),
                  enqueuedJobs = 1,
                ),
              ),
            buckets = JobBucketDepths(deadJobs = 3),
            scheduler =
              SchedulerHealth(
                schedulerEnabled = true,
                promoterEnabled = true,
                checkedAt = now,
              ),
          )
        },
        statusEvaluator =
          ThresholdBasedMestriHealthStatusEvaluator(
            ThresholdBasedMestriHealthThresholds(maxDeadJobs = 2),
          ),
        clock = { checkedAt },
      )

    val snapshot = service.health()

    assertEquals(MestriHealthStatus.DOWN, snapshot.status)
    assertEquals("no active workers while jobs are queued", snapshot.details["reason"])
  }
}
