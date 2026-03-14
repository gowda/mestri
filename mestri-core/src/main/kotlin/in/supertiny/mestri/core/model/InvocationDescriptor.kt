package `in`.supertiny.mestri.core.model

data class InvocationDescriptor(
  val target: InvocationTarget,
  val method: InvocationMethod,
  val arguments: List<InvocationArgument> = emptyList(),
  val labels: Set<String> = emptySet(),
) {
  init {
    require(labels.none { it.isBlank() }) { "Invocation labels must not be blank." }
    require(arguments.map { it.typeName } == method.parameterTypeNames) {
      "Invocation arguments must match method parameter types by position."
    }
  }
}
