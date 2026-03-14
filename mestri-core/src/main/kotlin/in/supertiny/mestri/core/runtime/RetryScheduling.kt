package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant

data class RetryScheduling(
  val jobId: JobId,
  val bucket: JobBucket = JobBucket.RETRY,
  val attempt: Int,
  val dueAt: Instant,
) {
  init {
    require(bucket == JobBucket.RETRY) {
      "Retry scheduling bucket must be RETRY."
    }
    require(attempt > 0) {
      "Retry scheduling attempt must be positive."
    }
  }
}
