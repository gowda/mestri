package `in`.supertiny.mestri.core.serialization

interface PayloadSerializer<T : Any> {
  fun serialize(value: T): SerializedPayload

  fun deserialize(payload: SerializedPayload): T
}
