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

fun interface MestriHealthStatusEvaluator {
  fun evaluate(observation: MestriHealthObservation): MestriHealthStatus
}

class DefaultMestriHealthStatusEvaluator : MestriHealthStatusEvaluator {
  override fun evaluate(observation: MestriHealthObservation): MestriHealthStatus =
    if (observation.redis.reachable) {
      MestriHealthStatus.UP
    } else {
      MestriHealthStatus.DOWN
    }
}

class MestriHealthService(
  private val observationSource: MestriHealthObservationSource,
  private val statusEvaluator: MestriHealthStatusEvaluator = DefaultMestriHealthStatusEvaluator(),
  private val clock: () -> Instant = { Instant.now() },
) {
  fun health(): MestriHealthSnapshot {
    val checkedAt = clock()
    val observation = observationSource.observe(checkedAt)

    return MestriHealthSnapshot(
      status = statusEvaluator.evaluate(observation),
      checkedAt = checkedAt,
      redis = observation.redis,
      workers = observation.workers,
      queues = observation.queues,
      buckets = observation.buckets,
      scheduler = observation.scheduler,
      details = observation.details,
    )
  }
}
