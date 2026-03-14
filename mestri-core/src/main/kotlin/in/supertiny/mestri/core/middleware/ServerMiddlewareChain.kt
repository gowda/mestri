package `in`.supertiny.mestri.core.middleware

class ServerMiddlewareChain(
  private val middlewares: List<ServerMiddleware>,
  private val terminal: ServerChain,
) : ServerChain {
  override fun proceed(request: ServerExecutionRequest): ServerExecutionResult =
    DelegatingServerChain(
      middlewares = middlewares,
      terminal = terminal,
      index = 0,
    ).proceed(request)

  private class DelegatingServerChain(
    private val middlewares: List<ServerMiddleware>,
    private val terminal: ServerChain,
    private val index: Int,
  ) : ServerChain {
    override fun proceed(request: ServerExecutionRequest): ServerExecutionResult {
      val middleware = middlewares.getOrNull(index) ?: return terminal.proceed(request)
      val next =
        DelegatingServerChain(
          middlewares = middlewares,
          terminal = terminal,
          index =
            index + 1,
        )
      return middleware.invoke(request, next)
    }
  }
}
