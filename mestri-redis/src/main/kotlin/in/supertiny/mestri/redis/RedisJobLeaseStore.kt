package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.runtime.JobLease
import `in`.supertiny.mestri.core.runtime.WorkerId
import java.time.Instant

class RedisJobLeaseStore(
  private val hashAccessor: RedisHashAccessor,
  private val setAccessor: RedisSetAccessor,
) {
  fun save(lease: JobLease) {
    hashAccessor.putAll(
      RedisKeyspace.workerLease(lease.jobId),
      mapOf(
        FIELD_JOB_ID to lease.jobId.value,
        FIELD_WORKER_ID to lease.workerId.value,
        FIELD_ACQUIRED_AT to lease.acquiredAt.toString(),
        FIELD_EXPIRES_AT to lease.expiresAt.toString(),
      ),
    )
    setAccessor.add(RedisKeyspace.leaseRegistry(), lease.jobId.value)
  }

  fun find(jobId: JobId): JobLease? {
    val fields = hashAccessor.entries(RedisKeyspace.workerLease(jobId))
    if (fields.isEmpty()) return null

    return JobLease(
      jobId = JobId(requireField(fields, FIELD_JOB_ID)),
      workerId = WorkerId(requireField(fields, FIELD_WORKER_ID)),
      acquiredAt = Instant.parse(requireField(fields, FIELD_ACQUIRED_AT)),
      expiresAt = Instant.parse(requireField(fields, FIELD_EXPIRES_AT)),
    )
  }

  fun delete(jobId: JobId) {
    hashAccessor.delete(RedisKeyspace.workerLease(jobId))
    setAccessor.remove(RedisKeyspace.leaseRegistry(), jobId.value)
  }

  fun findAll(): List<JobLease> =
    setAccessor.members(RedisKeyspace.leaseRegistry())
      .map(::JobId)
      .mapNotNull(::find)

  private fun requireField(
    fields: Map<String, String>,
    name: String,
  ): String =
    fields[name]?.takeIf { it.isNotBlank() }
      ?: throw IllegalArgumentException("Redis lease field '$name' is required.")

  companion object {
    private const val FIELD_JOB_ID = "jobId"
    private const val FIELD_WORKER_ID = "workerId"
    private const val FIELD_ACQUIRED_AT = "acquiredAt"
    private const val FIELD_EXPIRES_AT = "expiresAt"
  }
}
