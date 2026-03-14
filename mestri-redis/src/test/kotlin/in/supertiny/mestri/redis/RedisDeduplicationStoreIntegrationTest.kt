package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.DeduplicationKey
import `in`.supertiny.mestri.core.model.DeduplicationPolicy
import `in`.supertiny.mestri.core.model.DeduplicationScope
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

class RedisDeduplicationStoreIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `deduplication claims accept reject and expire in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val store = RedisDeduplicationStore(newHashAccessor(redisTemplate))
    val key = DeduplicationKey("invoice-user-1")
    val policy = DeduplicationPolicy(scope = DeduplicationScope.ENQUEUE, ttl = 10.minutes)

    val firstClaim =
      store.claim(
        key = key,
        policy = policy,
        jobId = JobId("job-123"),
        now = Instant.parse("2026-03-15T10:00:00Z"),
      )
    val secondClaim =
      store.claim(
        key = key,
        policy = policy,
        jobId = JobId("job-456"),
        now = Instant.parse("2026-03-15T10:05:00Z"),
      )
    val thirdClaim =
      store.claim(
        key = key,
        policy = policy,
        jobId = JobId("job-789"),
        now = Instant.parse("2026-03-15T10:10:01Z"),
      )

    assertEquals(
      JobId("job-123"),
      assertIs<RedisDeduplicationClaimAccepted>(firstClaim).record.jobId,
    )
    assertEquals(
      JobId("job-123"),
      assertIs<RedisDeduplicationClaimRejected>(secondClaim).existingRecord.jobId,
    )
    assertEquals(
      JobId("job-789"),
      assertIs<RedisDeduplicationClaimAccepted>(thirdClaim).record.jobId,
    )
    assertEquals(JobId("job-789"), store.find(key)?.jobId)
  }
}
