package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant

data class DeadLettering(
  val jobId: JobId,
  val bucket: JobBucket = JobBucket.DEAD,
  val recordedAt: Instant,
) {
  init {
    require(bucket == JobBucket.DEAD) {
      "Dead lettering bucket must be DEAD."
    }
  }
}
