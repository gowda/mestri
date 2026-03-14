package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant

class RedisImmediateJobEnqueuer(
  private val jobRecordStore: RedisJobRecordStore,
  private val listAccessor: RedisListAccessor,
  private val deduplicationStore: RedisDeduplicationStore? = null,
  private val clock: () -> Instant = { Instant.now() },
) {
  fun enqueue(payload: JobPayload): RedisImmediateEnqueueResult {
    val now = clock()
    val queueKey = RedisKeyspace.queue(payload.queue)
    val deduplicationKey = payload.deduplicationKey
    val deduplicationPolicy = payload.deduplicationPolicy
    if (
      deduplicationStore != null &&
      deduplicationKey != null &&
      deduplicationPolicy?.scope == `in`.supertiny.mestri.core.model.DeduplicationScope.ENQUEUE
    ) {
      when (
        val claim =
          deduplicationStore.claim(
            key = deduplicationKey,
            policy = deduplicationPolicy,
            jobId = payload.jobId,
            now = now,
          )
      ) {
        is RedisDeduplicationClaimRejected ->
          return RedisImmediateEnqueueResult(
            jobId = claim.existingRecord.jobId,
            queue = payload.queue,
            queueKey = queueKey,
            accepted = false,
            deduplicatedBy = claim.existingRecord.jobId,
          )

        is RedisDeduplicationClaimAccepted -> Unit
      }
    }

    val record =
      RedisJobRecord(
        jobId = payload.jobId,
        bucket = JobBucket.QUEUED,
        payload = payload,
        createdAt = now,
        updatedAt = now,
      )

    jobRecordStore.save(record)
    listAccessor.rightPush(queueKey, payload.jobId.value)

    return RedisImmediateEnqueueResult(
      jobId = payload.jobId,
      queue = payload.queue,
      queueKey = queueKey,
    )
  }
}
