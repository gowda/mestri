package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.middleware.ServerExecutionResult

fun interface JobSuccessHandler {
  fun handle(
    reservation: JobReservation,
    result: ServerExecutionResult,
  ): SuccessfulExecution
}
