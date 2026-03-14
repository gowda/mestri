package `in`.supertiny.mestri.core.middleware

fun interface ClientChain {
  fun proceed(request: ClientEnqueueRequest): ClientEnqueueResult
}
