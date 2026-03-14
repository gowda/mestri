package `in`.supertiny.mestri.core.runtime

import java.time.Duration
import java.time.Instant

data class WorkerHeartbeat(
  val workerId: WorkerId,
  val processId: ProcessId,
  val queues: Set<String> = emptySet(),
  val recordedAt: Instant,
  val expiresAt: Instant,
) {
  init {
    require(
      queues.none { it.isBlank() },
    ) { "Worker heartbeat queues must not contain blank names." }
    require(
      expiresAt.isAfter(recordedAt),
    ) { "Worker heartbeat expiresAt must be after recordedAt." }
  }

  fun isStale(at: Instant): Boolean = !expiresAt.isAfter(at)

  fun ttl(at: Instant): Duration =
    if (isStale(at)) Duration.ZERO else Duration.between(at, expiresAt)
}
