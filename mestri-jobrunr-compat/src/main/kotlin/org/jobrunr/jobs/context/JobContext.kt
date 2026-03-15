package org.jobrunr.jobs.context

import org.jobrunr.jobs.JobId

interface JobContext {
  val jobId: JobId?
  val queue: String?
  val attempt: Int?
  val metadata: Map<String, String>
}

object NoOpJobContext : JobContext {
  override val jobId: JobId? = null
  override val queue: String? = null
  override val attempt: Int? = null
  override val metadata: Map<String, String> = emptyMap()
}

data class MestriJobContext(
  override val jobId: JobId,
  override val queue: String,
  override val attempt: Int,
  override val metadata: Map<String, String> = emptyMap(),
) : JobContext {
  init {
    require(queue.isNotBlank()) { "JobContext queue must not be blank." }
    require(attempt >= 1) { "JobContext attempt must be at least 1." }
    require(metadata.keys.none { it.isBlank() }) { "JobContext metadata keys must not be blank." }
    require(metadata.values.none { it.isBlank() }) {
      "JobContext metadata values must not be blank."
    }
  }
}
