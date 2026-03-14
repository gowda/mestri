package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.middleware.ServerExecutionRequest

fun interface JobHandlerInvoker {
  fun invoke(reservation: JobReservation, request: ServerExecutionRequest)
}
