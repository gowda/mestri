package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.middleware.ServerChain
import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.middleware.ServerExecutionRequest
import `in`.supertiny.mestri.core.middleware.ServerExecutionResult
import `in`.supertiny.mestri.core.middleware.ServerMiddleware
import `in`.supertiny.mestri.core.middleware.ServerMiddlewareChain

class ServerExecutionPipeline(
  private val middlewares: List<ServerMiddleware>,
  private val handlerInvoker: JobHandlerInvoker,
) {
  fun execute(
    reservation: JobReservation,
    attempt: Int = 1,
    metadata: Map<String, String> = emptyMap(),
  ): ServerExecutionResult {
    val request =
      ServerExecutionRequest(
        jobId = reservation.payload.jobId,
        invocation = reservation.payload.invocation,
        queue = reservation.payload.queue,
        attempt = attempt,
        metadata = metadata,
      )
    val chain =
      ServerMiddlewareChain(
        middlewares = middlewares,
        terminal =
          ServerChain { terminalRequest ->
            handlerInvoker.invoke(reservation, terminalRequest)
            ServerExecutionResult(
              outcome = ServerExecutionOutcome.SUCCEEDED,
              metadata = terminalRequest.metadata,
            )
          },
      )

    return try {
      chain.proceed(request)
    } catch (exception: Exception) {
      ServerExecutionResult(
        outcome = ServerExecutionOutcome.TERMINAL_FAILURE,
        metadata = request.metadata,
        errorMessage =
          exception.message ?: exception::class.qualifiedName ?: "Handler invocation failed",
      )
    }
  }
}
