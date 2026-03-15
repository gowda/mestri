package org.jobrunr.jobs

import `in`.supertiny.mestri.core.model.JobId as MestriJobId

@JvmInline
value class JobId(
  val value: String,
) {
  init {
    require(value.isNotBlank()) { "JobId value must not be blank." }
  }

  override fun toString(): String = value

  fun toMestriJobId(): MestriJobId = MestriJobId(value)

  companion object {
    fun of(value: String): JobId = JobId(value)

    fun from(jobId: MestriJobId): JobId = JobId(jobId.value)
  }
}
