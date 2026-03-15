package `in`.supertiny.mestri.core.health

import `in`.supertiny.mestri.core.model.QueueName
import java.time.Duration
import java.time.Instant

enum class MestriHealthStatus {
  UP,
  DEGRADED,
  DOWN,
}

data class RedisHealth(
  val reachable: Boolean,
  val checkedAt: Instant,
  val errorMessage: String? = null,
) {
  init {
    require(errorMessage == null || errorMessage.isNotBlank()) {
      "Redis health errorMessage must not be blank when set."
    }
  }
}

data class WorkerHealth(
  val activeWorkers: Int,
  val staleWorkers: Int = 0,
  val staleLeases: Int = 0,
) {
  init {
    require(activeWorkers >= 0) { "Worker health activeWorkers must be zero or greater." }
    require(staleWorkers >= 0) { "Worker health staleWorkers must be zero or greater." }
    require(staleLeases >= 0) { "Worker health staleLeases must be zero or greater." }
  }
}

data class QueueDepth(
  val queue: QueueName,
  val enqueuedJobs: Long,
  val oldestJobAge: Duration? = null,
) {
  init {
    require(enqueuedJobs >= 0) { "Queue depth enqueuedJobs must be zero or greater." }
    require(oldestJobAge == null || !oldestJobAge.isNegative) {
      "Queue depth oldestJobAge must be zero or greater when set."
    }
  }
}

data class JobBucketDepths(
  val scheduledJobs: Long = 0,
  val retryJobs: Long = 0,
  val deadJobs: Long = 0,
  val succeededJobs: Long = 0,
  val deletedJobs: Long = 0,
) {
  init {
    require(scheduledJobs >= 0) { "Job bucket depth scheduledJobs must be zero or greater." }
    require(retryJobs >= 0) { "Job bucket depth retryJobs must be zero or greater." }
    require(deadJobs >= 0) { "Job bucket depth deadJobs must be zero or greater." }
    require(succeededJobs >= 0) { "Job bucket depth succeededJobs must be zero or greater." }
    require(deletedJobs >= 0) { "Job bucket depth deletedJobs must be zero or greater." }
  }
}

data class SchedulerHealth(
  val schedulerEnabled: Boolean,
  val promoterEnabled: Boolean,
  val checkedAt: Instant,
)

data class MestriHealthSnapshot(
  val status: MestriHealthStatus,
  val checkedAt: Instant,
  val redis: RedisHealth,
  val workers: WorkerHealth,
  val queues: List<QueueDepth> = emptyList(),
  val buckets: JobBucketDepths = JobBucketDepths(),
  val scheduler: SchedulerHealth,
  val details: Map<String, String> = emptyMap(),
) {
  init {
    require(details.keys.none { it.isBlank() }) {
      "Mestri health details keys must not be blank."
    }
    require(details.values.none { it.isBlank() }) {
      "Mestri health details values must not be blank."
    }
  }
}
