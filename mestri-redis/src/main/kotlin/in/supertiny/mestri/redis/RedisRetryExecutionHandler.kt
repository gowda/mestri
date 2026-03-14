package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.middleware.ServerExecutionResult
import `in`.supertiny.mestri.core.runtime.JobReservation
import `in`.supertiny.mestri.core.runtime.JobRetryHandler
import `in`.supertiny.mestri.core.runtime.RetryBackoffCalculator
import `in`.supertiny.mestri.core.runtime.RetryScheduling
import java.time.Instant
import kotlin.time.toJavaDuration

class RedisRetryExecutionHandler(
  private val scheduledJobStore: RedisScheduledJobStore,
  private val leaseStore: RedisJobLeaseStore,
  private val backoffCalculator: RetryBackoffCalculator = RetryBackoffCalculator(),
  private val clock: () -> Instant = { Instant.now() },
) : JobRetryHandler {
  override fun handle(
    reservation: JobReservation,
    result: ServerExecutionResult,
    attempt: Int,
  ): RetryScheduling {
    require(result.outcome == ServerExecutionOutcome.RETRYABLE_FAILURE) {
      "Retry execution handling requires a RETRYABLE_FAILURE server result."
    }
    require(attempt > 0) {
      "Retry execution handling requires a positive attempt."
    }

    val retryPolicy = reservation.payload.retryPolicy
    require(retryPolicy.maxAttempts > 0) {
      "Retry execution handling requires retries to be enabled."
    }
    require(attempt < retryPolicy.maxAttempts) {
      "Retry execution handling requires remaining retry attempts."
    }

    val nextAttempt = attempt + 1
    val dueAt = clock().plus(backoffCalculator.nextDelay(retryPolicy, attempt).toJavaDuration())
    val failureMetadata =
      buildMap {
        putAll(reservation.payload.metadata)
        putAll(result.metadata)
        result.errorMessage?.let { put(METADATA_LAST_ERROR_MESSAGE, it) }
        put(METADATA_LAST_FAILURE_AT, clock().toString())
        put(METADATA_NEXT_ATTEMPT, nextAttempt.toString())
      }
    val retryPayload = reservation.payload.copy(metadata = failureMetadata)

    scheduledJobStore.persistRetry(
      payload = retryPayload,
      dueAt = dueAt,
    )
    leaseStore.delete(reservation.payload.jobId)

    return RetryScheduling(
      jobId = reservation.payload.jobId,
      attempt = nextAttempt,
      dueAt = dueAt,
    )
  }

  companion object {
    const val METADATA_LAST_ERROR_MESSAGE: String = "mestri.lastErrorMessage"
    const val METADATA_LAST_FAILURE_AT: String = "mestri.lastFailureAt"
    const val METADATA_NEXT_ATTEMPT: String = "mestri.nextAttempt"
  }
}
