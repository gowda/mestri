package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant

class RedisTerminalJobStore(
  private val jobRecordStore: RedisJobRecordStore,
  private val sortedSetAccessor: RedisSortedSetAccessor,
  private val clock: () -> Instant = { Instant.now() },
) {

  fun persistSucceeded(payload: JobPayload, recordedAt: Instant = clock()): RedisTerminalPersistenceResult =
    persist(bucket = JobBucket.SUCCEEDED, bucketKey = RedisKeyspace.succeeded(), payload = payload, recordedAt = recordedAt)

  fun persistDeleted(payload: JobPayload, recordedAt: Instant = clock()): RedisTerminalPersistenceResult =
    persist(bucket = JobBucket.DELETED, bucketKey = RedisKeyspace.deleted(), payload = payload, recordedAt = recordedAt)

  private fun persist(
    bucket: JobBucket,
    bucketKey: String,
    payload: JobPayload,
    recordedAt: Instant,
  ): RedisTerminalPersistenceResult {
    val record =
      RedisJobRecord(
        jobId = payload.jobId,
        bucket = bucket,
        payload = payload,
        createdAt = recordedAt,
        updatedAt = recordedAt,
      )

    jobRecordStore.save(record)
    sortedSetAccessor.add(bucketKey, payload.jobId.value, recordedAt.toEpochMilli().toDouble())

    return RedisTerminalPersistenceResult(
      jobId = payload.jobId,
      bucket = bucket,
      bucketKey = bucketKey,
      recordedAt = recordedAt,
    )
  }
}
