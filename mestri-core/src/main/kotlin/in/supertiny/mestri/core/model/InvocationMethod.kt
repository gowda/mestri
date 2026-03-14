package `in`.supertiny.mestri.core.model

data class InvocationMethod(
  val name: String,
  val parameterTypeNames: List<String> = emptyList(),
) {
  init {
    require(name.isNotBlank()) { "Invocation method name must not be blank." }
    require(parameterTypeNames.none { it.isBlank() }) {
      "Invocation method parameter type names must not be blank."
    }
  }
}
