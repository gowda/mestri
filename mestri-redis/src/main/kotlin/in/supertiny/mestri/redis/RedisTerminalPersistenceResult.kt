package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant

data class RedisTerminalPersistenceResult(
  val jobId: JobId,
  val bucket: JobBucket,
  val bucketKey: String,
  val recordedAt: Instant,
)
