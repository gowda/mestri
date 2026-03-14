package `in`.supertiny.mestri.core.model

@JvmInline
value class DeduplicationKey(
  val value: String,
) {
  init {
    require(value.isNotBlank()) { "Deduplication key must not be blank." }
  }
}
