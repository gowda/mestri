package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName

data class RedisImmediateEnqueueResult(
  val jobId: JobId,
  val queue: QueueName,
  val queueKey: String,
  val accepted: Boolean = true,
  val deduplicatedBy: JobId? = null,
)
