package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant

data class JobLease(
  val jobId: JobId,
  val workerId: WorkerId,
  val acquiredAt: Instant,
  val expiresAt: Instant,
) {
  init {
    require(expiresAt.isAfter(acquiredAt)) { "Job lease expiresAt must be after acquiredAt." }
  }

  fun isExpired(at: Instant): Boolean = !expiresAt.isAfter(at)
}
