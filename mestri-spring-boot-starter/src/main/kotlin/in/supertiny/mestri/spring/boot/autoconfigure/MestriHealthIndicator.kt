package `in`.supertiny.mestri.spring.boot.autoconfigure

import `in`.supertiny.mestri.core.health.MestriHealthService
import `in`.supertiny.mestri.core.health.MestriHealthStatus
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status

class MestriHealthIndicator(
  private val healthService: MestriHealthService,
) : HealthIndicator {
  override fun health(): Health {
    val snapshot = healthService.health()

    return Health
      .status(snapshot.status.toSpringStatus())
      .withDetail("checkedAt", snapshot.checkedAt.toString())
      .withDetail("redisReachable", snapshot.redis.reachable)
      .withDetail("activeWorkers", snapshot.workers.activeWorkers)
      .withDetail("staleWorkers", snapshot.workers.staleWorkers)
      .withDetail("staleLeases", snapshot.workers.staleLeases)
      .withDetail("scheduledJobs", snapshot.buckets.scheduledJobs)
      .withDetail("retryJobs", snapshot.buckets.retryJobs)
      .withDetail("deadJobs", snapshot.buckets.deadJobs)
      .withDetail("succeededJobs", snapshot.buckets.succeededJobs)
      .withDetail("deletedJobs", snapshot.buckets.deletedJobs)
      .withDetail("schedulerEnabled", snapshot.scheduler.schedulerEnabled)
      .withDetail("promoterEnabled", snapshot.scheduler.promoterEnabled)
      .withDetail(
        "queues",
        snapshot.queues.associate { queueDepth ->
          queueDepth.queue.value to
            mapOf(
              "enqueuedJobs" to queueDepth.enqueuedJobs,
              "oldestJobAge" to queueDepth.oldestJobAge?.toString(),
            )
        },
      ).withDetails(snapshot.details)
      .build()
  }

  private fun MestriHealthStatus.toSpringStatus(): Status =
    when (this) {
      MestriHealthStatus.UP -> Status.UP
      MestriHealthStatus.DEGRADED -> Status("DEGRADED")
      MestriHealthStatus.DOWN -> Status.DOWN
    }
}
