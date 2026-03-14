package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant

data class RedisScheduledPersistenceResult(
  val jobId: JobId,
  val bucket: JobBucket,
  val bucketKey: String,
  val dueAt: Instant,
  val accepted: Boolean = true,
  val deduplicatedBy: JobId? = null,
)
