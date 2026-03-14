package `in`.supertiny.mestri.core.serialization

data class SerializedPayload(
  val format: PayloadFormat,
  val content: String,
) {
  init {
    require(content.isNotBlank()) { "Serialized payload content must not be blank." }
  }
}
