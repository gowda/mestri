package `in`.supertiny.mestri.core.middleware

fun interface ServerChain {
  fun proceed(request: ServerExecutionRequest): ServerExecutionResult
}
