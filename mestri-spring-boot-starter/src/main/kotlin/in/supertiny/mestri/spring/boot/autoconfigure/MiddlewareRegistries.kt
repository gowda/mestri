package `in`.supertiny.mestri.spring.boot.autoconfigure

import `in`.supertiny.mestri.core.middleware.ClientMiddleware
import `in`.supertiny.mestri.core.middleware.ServerMiddleware

data class ClientMiddlewareRegistry(
  val middlewares: List<ClientMiddleware>,
)

data class ServerMiddlewareRegistry(
  val middlewares: List<ServerMiddleware>,
)
