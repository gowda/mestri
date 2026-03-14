package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.middleware.ServerExecutionResult

fun interface JobRetryHandler {
  fun handle(
    reservation: JobReservation,
    result: ServerExecutionResult,
    attempt: Int,
  ): RetryScheduling
}
