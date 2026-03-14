package `in`.supertiny.mestri.core.middleware

class ClientMiddlewareChain(
  private val middlewares: List<ClientMiddleware>,
  private val terminal: ClientChain,
) : ClientChain {

  override fun proceed(request: ClientEnqueueRequest): ClientEnqueueResult {
    return DelegatingClientChain(
      middlewares = middlewares,
      terminal = terminal,
      index = 0,
    ).proceed(request)
  }

  private class DelegatingClientChain(
    private val middlewares: List<ClientMiddleware>,
    private val terminal: ClientChain,
    private val index: Int,
  ) : ClientChain {

    override fun proceed(request: ClientEnqueueRequest): ClientEnqueueResult {
      val middleware = middlewares.getOrNull(index) ?: return terminal.proceed(request)
      val next = DelegatingClientChain(middlewares = middlewares, terminal = terminal, index = index + 1)
      return middleware.invoke(request, next)
    }
  }
}
