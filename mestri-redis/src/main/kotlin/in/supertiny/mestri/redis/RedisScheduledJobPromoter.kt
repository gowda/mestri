package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant

data class RedisScheduledPromotionResult(
  val promotedJobIds: List<JobId>,
  val skippedJobIds: List<JobId>,
)

class RedisScheduledJobPromoter(
  private val jobRecordStore: RedisJobRecordStore,
  private val sortedSetAccessor: RedisSortedSetAccessor,
  private val listAccessor: RedisListAccessor,
  private val clock: () -> Instant = { Instant.now() },
) {
  fun promoteDueScheduled(now: Instant = clock()): RedisScheduledPromotionResult =
    promoteDueBucket(
      bucketKey = RedisKeyspace.scheduled(),
      expectedBucket = JobBucket.SCHEDULED,
      now = now,
    )

  fun promoteDueRetry(now: Instant = clock()): RedisScheduledPromotionResult =
    promoteDueBucket(
      bucketKey = RedisKeyspace.retry(),
      expectedBucket = JobBucket.RETRY,
      now = now,
    )

  private fun promoteDueBucket(
    bucketKey: String,
    expectedBucket: JobBucket,
    now: Instant,
  ): RedisScheduledPromotionResult {
    val dueJobIds =
      sortedSetAccessor
        .rangeByScore(
          key = bucketKey,
          minScoreInclusive = Double.NEGATIVE_INFINITY,
          maxScoreInclusive = now.toEpochMilli().toDouble(),
        ).map(::JobId)
    val promotedJobIds = mutableListOf<JobId>()
    val skippedJobIds = mutableListOf<JobId>()

    dueJobIds.forEach { jobId ->
      val record = jobRecordStore.find(jobId)
      if (record == null || record.bucket != expectedBucket) {
        skippedJobIds += jobId
        return@forEach
      }

      val updatedRecord =
        record.copy(
          bucket = JobBucket.QUEUED,
          updatedAt = now,
        )
      jobRecordStore.save(updatedRecord)
      listAccessor.rightPush(
        RedisKeyspace.queue(updatedRecord.payload.queue),
        updatedRecord.jobId.value,
      )
      sortedSetAccessor.remove(bucketKey, updatedRecord.jobId.value)
      promotedJobIds += updatedRecord.jobId
    }

    return RedisScheduledPromotionResult(
      promotedJobIds = promotedJobIds,
      skippedJobIds = skippedJobIds,
    )
  }
}
