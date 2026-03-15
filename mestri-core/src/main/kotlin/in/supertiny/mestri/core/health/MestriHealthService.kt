package `in`.supertiny.mestri.core.health

import java.time.Instant

data class MestriHealthObservation(
  val checkedAt: Instant,
  val redis: RedisHealth,
  val workers: WorkerHealth,
  val queues: List<QueueDepth> = emptyList(),
  val buckets: JobBucketDepths = JobBucketDepths(),
  val scheduler: SchedulerHealth,
  val details: Map<String, String> = emptyMap(),
)

fun interface MestriHealthObservationSource {
  fun observe(now: Instant): MestriHealthObservation
}

data class MestriHealthAssessment(
  val status: MestriHealthStatus,
  val details: Map<String, String> = emptyMap(),
) {
  init {
    require(details.keys.none { it.isBlank() }) {
      "Mestri health assessment detail keys must not be blank."
    }
    require(details.values.none { it.isBlank() }) {
      "Mestri health assessment detail values must not be blank."
    }
  }
}

fun interface MestriHealthStatusEvaluator {
  fun evaluate(observation: MestriHealthObservation): MestriHealthAssessment
}

class DefaultMestriHealthStatusEvaluator : MestriHealthStatusEvaluator {
  override fun evaluate(observation: MestriHealthObservation): MestriHealthAssessment =
    if (observation.redis.reachable) {
      MestriHealthAssessment(MestriHealthStatus.UP)
    } else {
      MestriHealthAssessment(
        status = MestriHealthStatus.DOWN,
        details = mapOf("reason" to "redis unreachable"),
      )
    }
}

data class ThresholdBasedMestriHealthThresholds(
  val maxEnqueuedJobs: Long? = null,
  val maxRetryJobs: Long? = null,
  val maxDeadJobs: Long? = null,
  val maxStaleLeases: Long? = null,
) {
  init {
    require(maxEnqueuedJobs == null || maxEnqueuedJobs >= 0) {
      "Health threshold maxEnqueuedJobs must be zero or greater."
    }
    require(maxRetryJobs == null || maxRetryJobs >= 0) {
      "Health threshold maxRetryJobs must be zero or greater."
    }
    require(maxDeadJobs == null || maxDeadJobs >= 0) {
      "Health threshold maxDeadJobs must be zero or greater."
    }
    require(maxStaleLeases == null || maxStaleLeases >= 0) {
      "Health threshold maxStaleLeases must be zero or greater."
    }
  }
}

class ThresholdBasedMestriHealthStatusEvaluator(
  private val thresholds: ThresholdBasedMestriHealthThresholds,
) : MestriHealthStatusEvaluator {
  override fun evaluate(observation: MestriHealthObservation): MestriHealthAssessment {
    if (!observation.redis.reachable) {
      return MestriHealthAssessment(
        status = MestriHealthStatus.DOWN,
        details = mapOf("reason" to "redis unreachable"),
      )
    }

    val totalEnqueuedJobs = observation.queues.sumOf { queue -> queue.enqueuedJobs }

    if (totalEnqueuedJobs > 0 && observation.workers.activeWorkers == 0) {
      return MestriHealthAssessment(
        status = MestriHealthStatus.DOWN,
        details = mapOf("reason" to "no active workers while jobs are queued"),
      )
    }

    evaluateDownReason(observation)?.let { reason ->
      return MestriHealthAssessment(
        status = MestriHealthStatus.DOWN,
        details = mapOf("reason" to reason),
      )
    }

    evaluateDegradedReason(observation)?.let { reason ->
      return MestriHealthAssessment(
        status = MestriHealthStatus.DEGRADED,
        details = mapOf("reason" to reason),
      )
    }

    return MestriHealthAssessment(MestriHealthStatus.UP)
  }

  private fun evaluateDownReason(observation: MestriHealthObservation): String? =
    when {
      exceeds(
        value = observation.workers.staleLeases.toLong(),
        threshold = thresholds.maxStaleLeases,
      ) -> "stale lease threshold exceeded"
      exceeds(
        value = observation.buckets.deadJobs,
        threshold = thresholds.maxDeadJobs,
      ) -> "dead job threshold exceeded"
      else -> null
    }

  private fun evaluateDegradedReason(observation: MestriHealthObservation): String? {
    val totalEnqueuedJobs = observation.queues.sumOf { queue -> queue.enqueuedJobs }

    return when {
      exceeds(
        value = totalEnqueuedJobs,
        threshold = thresholds.maxEnqueuedJobs,
      ) -> "enqueued job threshold exceeded"
      exceeds(
        value = observation.buckets.retryJobs,
        threshold = thresholds.maxRetryJobs,
      ) -> "retry job threshold exceeded"
      observation.workers.staleWorkers > 0 -> "stale workers detected"
      else -> null
    }
  }

  private fun exceeds(
    value: Long,
    threshold: Long?,
  ): Boolean = threshold != null && value > threshold
}

class MestriHealthService(
  private val observationSource: MestriHealthObservationSource,
  private val statusEvaluator: MestriHealthStatusEvaluator = DefaultMestriHealthStatusEvaluator(),
  private val clock: () -> Instant = { Instant.now() },
) {
  fun health(): MestriHealthSnapshot {
    val checkedAt = clock()
    val observation = observationSource.observe(checkedAt)
    val assessment = statusEvaluator.evaluate(observation)

    return MestriHealthSnapshot(
      status = assessment.status,
      checkedAt = checkedAt,
      redis = observation.redis,
      workers = observation.workers,
      queues = observation.queues,
      buckets = observation.buckets,
      scheduler = observation.scheduler,
      details = observation.details + assessment.details,
    )
  }
}
