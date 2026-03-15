package org.jobrunr.jobs

import `in`.supertiny.mestri.core.model.JobId as MestriJobId

@JvmInline
value class JobId(
  val value: String,
) {
  init {
    require(value.isNotBlank()) { "JobId value must not be blank." }
  }

  fun toMestriJobId(): MestriJobId = MestriJobId(value)

  companion object {
    fun from(jobId: MestriJobId): JobId = JobId(jobId.value)
  }
}
