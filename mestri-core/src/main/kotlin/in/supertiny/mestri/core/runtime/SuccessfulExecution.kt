package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant

data class SuccessfulExecution(
  val jobId: JobId,
  val bucket: JobBucket = JobBucket.SUCCEEDED,
  val completedAt: Instant,
) {
  init {
    require(bucket == JobBucket.SUCCEEDED) {
      "Successful execution bucket must be SUCCEEDED."
    }
  }
}
