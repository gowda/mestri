package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.DeduplicationScope
import `in`.supertiny.mestri.core.model.DeduplicationStrategy
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant

class RedisScheduledJobStore(
  private val jobRecordStore: RedisJobRecordStore,
  private val sortedSetAccessor: RedisSortedSetAccessor,
  private val deduplicationStore: RedisDeduplicationStore? = null,
  private val clock: () -> Instant = { Instant.now() },
) {
  fun persistScheduled(
    payload: JobPayload,
    dueAt: Instant,
  ): RedisScheduledPersistenceResult =
    persist(
      bucket = JobBucket.SCHEDULED,
      bucketKey = RedisKeyspace.scheduled(),
      payload = payload,
      dueAt = dueAt,
    )

  fun persistRetry(
    payload: JobPayload,
    dueAt: Instant,
  ): RedisScheduledPersistenceResult =
    persist(
      bucket = JobBucket.RETRY,
      bucketKey = RedisKeyspace.retry(),
      payload = payload,
      dueAt = dueAt,
    )

  private fun persist(
    bucket: JobBucket,
    bucketKey: String,
    payload: JobPayload,
    dueAt: Instant,
  ): RedisScheduledPersistenceResult {
    val now = clock()
    val deduplicationKey = payload.deduplicationKey
    val deduplicationPolicy = payload.deduplicationPolicy
    if (
      bucket == JobBucket.SCHEDULED &&
      deduplicationStore != null &&
      deduplicationKey != null &&
      deduplicationPolicy?.scope == DeduplicationScope.SCHEDULE
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
        is RedisDeduplicationClaimRejected -> {
          if (deduplicationPolicy.strategy == DeduplicationStrategy.COALESCE) {
            sortedSetAccessor.add(
              bucketKey,
              claim.existingRecord.jobId.value,
              dueAt.toEpochMilli().toDouble(),
            )
          }
          return RedisScheduledPersistenceResult(
            jobId = claim.existingRecord.jobId,
            bucket = bucket,
            bucketKey = bucketKey,
            dueAt = dueAt,
            accepted = false,
            deduplicatedBy = claim.existingRecord.jobId,
          )
        }

        is RedisDeduplicationClaimAccepted -> Unit
      }
    }

    val record =
      RedisJobRecord(
        jobId = payload.jobId,
        bucket = bucket,
        payload = payload,
        createdAt = now,
        updatedAt = now,
      )

    jobRecordStore.save(record)
    sortedSetAccessor.add(bucketKey, payload.jobId.value, dueAt.toEpochMilli().toDouble())

    return RedisScheduledPersistenceResult(
      jobId = payload.jobId,
      bucket = bucket,
      bucketKey = bucketKey,
      dueAt = dueAt,
    )
  }
}
