package `in`.supertiny.mestri.core.middleware

enum class ServerExecutionOutcome {
  SUCCEEDED,
  RETRYABLE_FAILURE,
  TERMINAL_FAILURE,
}

data class ServerExecutionResult(
  val outcome: ServerExecutionOutcome,
  val metadata: Map<String, String> = emptyMap(),
  val errorMessage: String? = null,
) {
  init {
    require(errorMessage == null || errorMessage.isNotBlank()) {
      "Server execution errorMessage must not be blank when set."
    }
  }
}
