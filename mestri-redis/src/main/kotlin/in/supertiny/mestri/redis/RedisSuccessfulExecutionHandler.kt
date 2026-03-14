package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.middleware.ServerExecutionResult
import `in`.supertiny.mestri.core.runtime.JobReservation
import `in`.supertiny.mestri.core.runtime.JobSuccessHandler
import `in`.supertiny.mestri.core.runtime.SuccessfulExecution
import java.time.Instant

class RedisSuccessfulExecutionHandler(
  private val terminalJobStore: RedisTerminalJobStore,
  private val leaseStore: RedisJobLeaseStore,
  private val clock: () -> Instant = { Instant.now() },
) : JobSuccessHandler {

  override fun handle(
    reservation: JobReservation,
    result: ServerExecutionResult,
  ): SuccessfulExecution {
    require(result.outcome == ServerExecutionOutcome.SUCCEEDED) {
      "Successful execution handling requires a SUCCEEDED server result."
    }

    val completedAt = clock()
    val completedPayload =
      reservation.payload.copy(
        metadata = reservation.payload.metadata + result.metadata,
      )

    terminalJobStore.persistSucceeded(
      payload = completedPayload,
      recordedAt = completedAt,
    )
    leaseStore.delete(reservation.payload.jobId)

    return SuccessfulExecution(
      jobId = reservation.payload.jobId,
      completedAt = completedAt,
    )
  }
}
