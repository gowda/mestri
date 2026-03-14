package `in`.supertiny.mestri.core.model

data class InvocationArgument(
  val typeName: String,
  val serializedValue: String?,
) {
  init {
    require(typeName.isNotBlank()) { "Invocation argument typeName must not be blank." }
  }
}
