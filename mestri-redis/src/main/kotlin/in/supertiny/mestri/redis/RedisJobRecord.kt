package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant

data class RedisJobRecord(
  val jobId: JobId,
  val bucket: JobBucket,
  val payload: JobPayload,
  val createdAt: Instant,
  val updatedAt: Instant,
) {
  init {
    require(updatedAt == createdAt || updatedAt.isAfter(createdAt)) {
      "Redis job record updatedAt must be equal to or after createdAt."
    }
    require(payload.jobId == jobId) {
      "Redis job record payload jobId must match the record jobId."
    }
  }
}
