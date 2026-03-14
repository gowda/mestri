package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.runtime.CleanupStaleWorker
import `in`.supertiny.mestri.core.runtime.CleanupRetainedBucket
import `in`.supertiny.mestri.core.runtime.MaintenanceAction
import `in`.supertiny.mestri.core.runtime.MaintenanceExecutor
import `in`.supertiny.mestri.core.runtime.MaintenancePlan
import `in`.supertiny.mestri.core.runtime.MaintenanceReport
import `in`.supertiny.mestri.core.runtime.RequeueExpiredLease
import `in`.supertiny.mestri.core.runtime.RetryBackoffCalculator
import java.time.Instant
import kotlin.time.toJavaDuration

class RedisMaintenanceExecutor(
  private val jobRecordStore: RedisJobRecordStore,
  private val listAccessor: RedisListAccessor,
  private val scheduledJobStore: RedisScheduledJobStore,
  private val leaseStore: RedisJobLeaseStore,
  private val heartbeatStore: RedisWorkerHeartbeatStore,
  private val backoffCalculator: RetryBackoffCalculator = RetryBackoffCalculator(),
) : MaintenanceExecutor {
  override fun execute(
    plan: MaintenancePlan,
    now: Instant,
  ): MaintenanceReport {
    val completedActions = mutableListOf<MaintenanceAction>()
    val skippedActions = mutableListOf<MaintenanceAction>()

    plan.actions.forEach { action ->
      when (action) {
        is RequeueExpiredLease -> {
          if (recoverExpiredLease(action, now)) {
            completedActions += action
          } else {
            skippedActions += action
          }
        }

        is CleanupStaleWorker,
        -> {
          if (cleanupStaleWorker(action)) {
            completedActions += action
          } else {
            skippedActions += action
          }
        }

        is CleanupRetainedBucket -> skippedActions += action
      }
    }

    return MaintenanceReport(
      executedAt = now,
      completedActions = completedActions,
      skippedActions = skippedActions,
    )
  }

  private fun recoverExpiredLease(
    action: RequeueExpiredLease,
    now: Instant,
  ): Boolean {
    val record = jobRecordStore.find(action.jobId) ?: return false
    if (record.bucket != JobBucket.RUNNING) return false

    val payload = record.payload
    val currentAttempt = payload.metadata[RedisRetryExecutionHandler.METADATA_NEXT_ATTEMPT]?.toIntOrNull() ?: 1
    val metadata =
      payload.metadata +
        mapOf(
          METADATA_ORPHAN_RECOVERED_AT to now.toString(),
          METADATA_ORPHAN_WORKER_ID to action.workerId.value,
        )
    val updatedPayload = payload.copy(metadata = metadata)
    val retryPolicy = updatedPayload.retryPolicy

    if (retryPolicy.maxAttempts > currentAttempt) {
      val dueAt = now.plus(backoffCalculator.nextDelay(retryPolicy, currentAttempt).toJavaDuration())
      val retryPayload =
        updatedPayload.copy(
          metadata =
            updatedPayload.metadata +
              mapOf(
                RedisRetryExecutionHandler.METADATA_LAST_ERROR_MESSAGE to METADATA_ORPHAN_ERROR_MESSAGE,
                RedisRetryExecutionHandler.METADATA_LAST_FAILURE_AT to now.toString(),
                RedisRetryExecutionHandler.METADATA_NEXT_ATTEMPT to (currentAttempt + 1).toString(),
              ),
        )
      scheduledJobStore.persistRetry(retryPayload, dueAt)
    } else {
      val updatedRecord =
        record.copy(
          bucket = JobBucket.QUEUED,
          payload = updatedPayload,
          updatedAt = now,
        )
      jobRecordStore.save(updatedRecord)
      listAccessor.rightPush(RedisKeyspace.queue(updatedPayload.queue), updatedPayload.jobId.value)
    }

    leaseStore.delete(action.jobId)
    return true
  }

  private fun cleanupStaleWorker(action: CleanupStaleWorker): Boolean {
    val heartbeat = heartbeatStore.find(action.workerId) ?: return false
    heartbeatStore.delete(action.workerId)
    return heartbeatStore.find(action.workerId) == null &&
      action.workerId !in heartbeatStore.registeredWorkerIds() &&
      heartbeat.processId !in heartbeatStore.registeredProcessIds()
  }

  companion object {
    const val METADATA_ORPHAN_RECOVERED_AT: String = "mestri.orphanRecoveredAt"
    const val METADATA_ORPHAN_WORKER_ID: String = "mestri.orphanWorkerId"
    const val METADATA_ORPHAN_ERROR_MESSAGE: String = "Worker lease expired"
  }
}
