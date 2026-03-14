package `in`.supertiny.mestri.core.model

enum class JobBucket {
  QUEUED,
  RUNNING,
  SCHEDULED,
  RETRY,
  DEAD,
  SUCCEEDED,
  DELETED,
}
