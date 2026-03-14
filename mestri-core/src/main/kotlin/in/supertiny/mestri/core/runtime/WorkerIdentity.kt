package `in`.supertiny.mestri.core.runtime

@JvmInline
value class WorkerId(val value: String) {
  init {
    require(value.isNotBlank()) { "Worker id must not be blank." }
  }
}

@JvmInline
value class ProcessId(val value: String) {
  init {
    require(value.isNotBlank()) { "Process id must not be blank." }
  }
}
