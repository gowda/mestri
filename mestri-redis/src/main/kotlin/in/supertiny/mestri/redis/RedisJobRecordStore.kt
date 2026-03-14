package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobId

class RedisJobRecordStore(
  private val hashAccessor: RedisHashAccessor,
  private val mapper: RedisJobRecordMapper,
) {

  fun save(record: RedisJobRecord) {
    hashAccessor.putAll(RedisKeyspace.job(record.jobId), mapper.toHash(record))
  }

  fun find(jobId: JobId): RedisJobRecord? {
    val fields = hashAccessor.entries(RedisKeyspace.job(jobId))
    return if (fields.isEmpty()) null else mapper.fromHash(fields)
  }
}
