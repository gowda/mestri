package `in`.supertiny.mestri.core.health

import `in`.supertiny.mestri.core.model.QueueName
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ThresholdBasedMestriHealthStatusEvaluatorTest {
  private val checkedAt = Instant.parse("2026-03-15T12:00:00Z")

  @Test
  fun `evaluator returns up when observation is within thresholds`() {
    val evaluator =
      ThresholdBasedMestriHealthStatusEvaluator(
        thresholds =
          ThresholdBasedMestriHealthThresholds(
            maxEnqueuedJobs = 10,
            maxRetryJobs = 5,
            maxDeadJobs = 2,
            maxStaleLeases = 1,
          ),
      )

    val assessment = evaluator.evaluate(healthyObservation())

    assertEquals(MestriHealthStatus.UP, assessment.status)
    assertEquals(emptyMap(), assessment.details)
  }

  @Test
  fun `evaluator returns degraded when queue pressure exceeds thresholds`() {
    val evaluator =
      ThresholdBasedMestriHealthStatusEvaluator(
        thresholds = ThresholdBasedMestriHealthThresholds(maxEnqueuedJobs = 5),
      )

    val assessment =
      evaluator.evaluate(
        healthyObservation().copy(
          queues =
            listOf(
              QueueDepth(
                queue = QueueName("default"),
                enqueuedJobs = 6,
              ),
            ),
        ),
      )

    assertEquals(MestriHealthStatus.DEGRADED, assessment.status)
    assertEquals("enqueued job threshold exceeded", assessment.details["reason"])
  }

  @Test
  fun `evaluator returns down when redis is unreachable`() {
    val evaluator =
      ThresholdBasedMestriHealthStatusEvaluator(
        thresholds = ThresholdBasedMestriHealthThresholds(),
      )

    val assessment =
      evaluator.evaluate(
        healthyObservation().copy(
          redis =
            RedisHealth(
              reachable = false,
              checkedAt = checkedAt,
              errorMessage = "timeout",
            ),
        ),
      )

    assertEquals(MestriHealthStatus.DOWN, assessment.status)
    assertEquals("redis unreachable", assessment.details["reason"])
  }

  private fun healthyObservation(): MestriHealthObservation =
    MestriHealthObservation(
      checkedAt = checkedAt,
      redis = RedisHealth(reachable = true, checkedAt = checkedAt),
      workers = WorkerHealth(activeWorkers = 2),
      queues =
        listOf(
          QueueDepth(
            queue = QueueName("default"),
            enqueuedJobs = 2,
          ),
        ),
      buckets =
        JobBucketDepths(
          retryJobs = 1,
          deadJobs = 0,
        ),
      scheduler =
        SchedulerHealth(
          schedulerEnabled = true,
          promoterEnabled = true,
          checkedAt = checkedAt,
        ),
    )
}
