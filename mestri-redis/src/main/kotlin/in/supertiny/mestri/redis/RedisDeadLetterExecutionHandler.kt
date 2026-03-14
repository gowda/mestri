package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.middleware.ServerExecutionResult
import `in`.supertiny.mestri.core.runtime.DeadLettering
import `in`.supertiny.mestri.core.runtime.JobDeadLetterHandler
import `in`.supertiny.mestri.core.runtime.JobReservation
import java.time.Instant

class RedisDeadLetterExecutionHandler(
  private val scheduledJobStore: RedisScheduledJobStore,
  private val terminalJobStore: RedisTerminalJobStore,
  private val leaseStore: RedisJobLeaseStore,
  private val clock: () -> Instant = { Instant.now() },
) : JobDeadLetterHandler {

  override fun handle(
    reservation: JobReservation,
    result: ServerExecutionResult,
    attempt: Int,
  ): DeadLettering {
    require(attempt > 0) {
      "Dead-letter execution handling requires a positive attempt."
    }
    val retryPolicy = reservation.payload.retryPolicy
    val isExhaustedRetry = result.outcome == ServerExecutionOutcome.RETRYABLE_FAILURE && attempt >= retryPolicy.maxAttempts
    require(result.outcome == ServerExecutionOutcome.TERMINAL_FAILURE || isExhaustedRetry) {
      "Dead-letter execution handling requires a TERMINAL_FAILURE or exhausted retryable failure."
    }

    val recordedAt = clock()
    val deadPayload =
      reservation.payload.copy(
        metadata =
          buildMap {
            putAll(reservation.payload.metadata)
            putAll(result.metadata)
            result.errorMessage?.let { put(METADATA_LAST_ERROR_MESSAGE, it) }
            put(METADATA_DEAD_AT, recordedAt.toString())
            put(METADATA_FINAL_ATTEMPT, attempt.toString())
          },
      )

    terminalJobStore.persist(
      bucket = `in`.supertiny.mestri.core.model.JobBucket.DEAD,
      bucketKey = RedisKeyspace.dead(),
      payload = deadPayload,
      recordedAt = recordedAt,
    )
    leaseStore.delete(reservation.payload.jobId)

    return DeadLettering(
      jobId = reservation.payload.jobId,
      recordedAt = recordedAt,
    )
  }

  companion object {
    const val METADATA_LAST_ERROR_MESSAGE: String = "mestri.lastErrorMessage"
    const val METADATA_DEAD_AT: String = "mestri.deadAt"
    const val METADATA_FINAL_ATTEMPT: String = "mestri.finalAttempt"
  }
}
