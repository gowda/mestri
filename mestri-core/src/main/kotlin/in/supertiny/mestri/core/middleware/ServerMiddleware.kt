package `in`.supertiny.mestri.core.middleware

fun interface ServerMiddleware {
  fun invoke(request: ServerExecutionRequest, chain: ServerChain): ServerExecutionResult
}
