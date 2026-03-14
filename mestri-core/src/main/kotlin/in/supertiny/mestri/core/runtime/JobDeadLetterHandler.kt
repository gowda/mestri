package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.middleware.ServerExecutionResult

fun interface JobDeadLetterHandler {
  fun handle(
    reservation: JobReservation,
    result: ServerExecutionResult,
    attempt: Int,
  ): DeadLettering
}
