package `in`.supertiny.mestri.core.middleware

fun interface ClientMiddleware {
  fun invoke(
    request: ClientEnqueueRequest,
    chain: ClientChain,
  ): ClientEnqueueResult
}
