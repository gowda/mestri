package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant

data class JobReservation(
  val lease: JobLease,
  val payload: JobPayload,
  val reservedAt: Instant,
) {
  init {
    require(reservedAt == lease.acquiredAt) {
      "Job reservation reservedAt must match the lease acquiredAt timestamp."
    }
    require(payload.jobId == lease.jobId) {
      "Job reservation payload jobId must match the lease jobId."
    }
  }
}
