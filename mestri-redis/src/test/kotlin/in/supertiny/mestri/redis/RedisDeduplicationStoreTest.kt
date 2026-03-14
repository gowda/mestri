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

class RedisDeduplicationStoreTest {
  @Test
  fun `claim accepts new deduplication key`() {
    val store = RedisDeduplicationStore(InMemoryRedisHashAccessor())

    val result =
      store.claim(
        key = DeduplicationKey("invoice-user-1"),
        policy = DeduplicationPolicy(scope = DeduplicationScope.ENQUEUE, ttl = 10.minutes),
        jobId = JobId("job-123"),
        now = Instant.parse("2026-03-15T10:00:00Z"),
      )

    val accepted = assertIs<RedisDeduplicationClaimAccepted>(result)
    assertEquals(JobId("job-123"), accepted.record.jobId)
    assertEquals(Instant.parse("2026-03-15T10:10:00Z"), accepted.record.expiresAt)
  }

  @Test
  fun `claim rejects active deduplication key`() {
    val store = RedisDeduplicationStore(InMemoryRedisHashAccessor())
    val now = Instant.parse("2026-03-15T10:00:00Z")
    store.claim(
      key = DeduplicationKey("invoice-user-1"),
      policy = DeduplicationPolicy(scope = DeduplicationScope.ENQUEUE, ttl = 10.minutes),
      jobId = JobId("job-123"),
      now = now,
    )

    val result =
      store.claim(
        key = DeduplicationKey("invoice-user-1"),
        policy = DeduplicationPolicy(scope = DeduplicationScope.ENQUEUE, ttl = 10.minutes),
        jobId = JobId("job-456"),
        now = now.plusSeconds(60),
      )

    val rejected = assertIs<RedisDeduplicationClaimRejected>(result)
    assertEquals(JobId("job-123"), rejected.existingRecord.jobId)
    assertEquals(Instant.parse("2026-03-15T10:10:00Z"), rejected.existingRecord.expiresAt)
  }

  @Test
  fun `claim accepts expired deduplication key and replaces stored record`() {
    val store = RedisDeduplicationStore(InMemoryRedisHashAccessor())
    store.claim(
      key = DeduplicationKey("invoice-user-1"),
      policy = DeduplicationPolicy(scope = DeduplicationScope.ENQUEUE, ttl = 10.minutes),
      jobId = JobId("job-123"),
      now = Instant.parse("2026-03-15T10:00:00Z"),
    )

    val result =
      store.claim(
        key = DeduplicationKey("invoice-user-1"),
        policy = DeduplicationPolicy(scope = DeduplicationScope.ENQUEUE, ttl = 10.minutes),
        jobId = JobId("job-456"),
        now = Instant.parse("2026-03-15T10:10:01Z"),
      )

    val accepted = assertIs<RedisDeduplicationClaimAccepted>(result)
    assertEquals(JobId("job-456"), accepted.record.jobId)
    assertEquals(JobId("job-456"), store.find(DeduplicationKey("invoice-user-1"))?.jobId)
  }

  private class InMemoryRedisHashAccessor : RedisHashAccessor {
    private val data = mutableMapOf<String, Map<String, String>>()

    override fun entries(key: String): Map<String, String> = data[key].orEmpty()

    override fun putAll(
      key: String,
      fields: Map<String, String>,
    ) {
      data[key] = fields.toMap()
    }

    override fun delete(key: String) {
      data.remove(key)
    }
  }
}
