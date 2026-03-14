package `in`.supertiny.mestri.core.model

import java.util.UUID

@JvmInline
value class JobId(val value: String) {
  init {
    require(value.isNotBlank()) { "Job id must not be blank." }
  }

  companion object {
    fun random(): JobId = JobId(UUID.randomUUID().toString())
  }
}
