package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant

class RedisImmediateJobEnqueuer(
  private val jobRecordStore: RedisJobRecordStore,
  private val listAccessor: RedisListAccessor,
  private val clock: () -> Instant = { Instant.now() },
) {

  fun enqueue(payload: JobPayload): RedisImmediateEnqueueResult {
    val now = clock()
    val record =
      RedisJobRecord(
        jobId = payload.jobId,
        bucket = JobBucket.QUEUED,
        payload = payload,
        createdAt = now,
        updatedAt = now,
      )
    val queueKey = RedisKeyspace.queue(payload.queue)

    jobRecordStore.save(record)
    listAccessor.rightPush(queueKey, payload.jobId.value)

    return RedisImmediateEnqueueResult(
      jobId = payload.jobId,
      queue = payload.queue,
      queueKey = queueKey,
    )
  }
}
