package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.DeduplicationKey
import `in`.supertiny.mestri.core.model.DeduplicationPolicy
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant
import kotlin.time.toJavaDuration

data class RedisDeduplicationRecord(
  val key: DeduplicationKey,
  val jobId: JobId,
  val expiresAt: Instant,
)

sealed interface RedisDeduplicationClaimResult

data class RedisDeduplicationClaimAccepted(
  val record: RedisDeduplicationRecord,
) : RedisDeduplicationClaimResult

data class RedisDeduplicationClaimRejected(
  val existingRecord: RedisDeduplicationRecord,
) : RedisDeduplicationClaimResult

class RedisDeduplicationStore(
  private val hashAccessor: RedisHashAccessor,
) {
  fun claim(
    key: DeduplicationKey,
    policy: DeduplicationPolicy,
    jobId: JobId,
    now: Instant,
  ): RedisDeduplicationClaimResult {
    val existing = find(key)
    if (existing != null && !existing.expiresAt.isBefore(now)) {
      return RedisDeduplicationClaimRejected(existing)
    }

    val record =
      RedisDeduplicationRecord(
        key = key,
        jobId = jobId,
        expiresAt = now.plus(policy.ttl.toJavaDuration()),
      )
    save(record)
    return RedisDeduplicationClaimAccepted(record)
  }

  fun find(key: DeduplicationKey): RedisDeduplicationRecord? {
    val fields = hashAccessor.entries(RedisKeyspace.deduplication(key))
    if (fields.isEmpty()) return null
    return RedisDeduplicationRecord(
      key = DeduplicationKey(requireField(fields, FIELD_KEY)),
      jobId = JobId(requireField(fields, FIELD_JOB_ID)),
      expiresAt = Instant.parse(requireField(fields, FIELD_EXPIRES_AT)),
    )
  }

  private fun save(record: RedisDeduplicationRecord) {
    hashAccessor.putAll(
      RedisKeyspace.deduplication(record.key),
      mapOf(
        FIELD_KEY to record.key.value,
        FIELD_JOB_ID to record.jobId.value,
        FIELD_EXPIRES_AT to record.expiresAt.toString(),
      ),
    )
  }

  private fun requireField(
    fields: Map<String, String>,
    name: String,
  ): String =
    fields[name]?.takeIf { it.isNotBlank() }
      ?: throw IllegalArgumentException("Redis deduplication field '$name' is required.")

  companion object {
    private const val FIELD_KEY = "key"
    private const val FIELD_JOB_ID = "jobId"
    private const val FIELD_EXPIRES_AT = "expiresAt"
  }
}
