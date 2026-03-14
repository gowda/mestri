package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.JobId

sealed interface MaintenanceAction

data class RequeueExpiredLease(
  val jobId: JobId,
  val workerId: WorkerId,
) : MaintenanceAction

data class CleanupStaleWorker(
  val workerId: WorkerId,
) : MaintenanceAction

data class CleanupRetainedBucket(
  val bucketName: String,
  val maxEntries: Long? = null,
) : MaintenanceAction {
  init {
    require(bucketName.isNotBlank()) { "Maintenance cleanup bucketName must not be blank." }
    require(maxEntries == null || maxEntries >= 0) {
      "Maintenance cleanup maxEntries must be zero or greater when set."
    }
  }
}
