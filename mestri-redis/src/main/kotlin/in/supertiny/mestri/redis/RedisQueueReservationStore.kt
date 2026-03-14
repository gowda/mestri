package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.runtime.JobLease
import `in`.supertiny.mestri.core.runtime.JobReservation
import `in`.supertiny.mestri.core.runtime.WorkerId
import java.time.Duration
import java.time.Instant

class RedisQueueReservationStore(
  private val queueAccessor: RedisQueueAccessor,
  private val jobRecordStore: RedisJobRecordStore,
  private val leaseStore: RedisJobLeaseStore,
  private val clock: () -> Instant = { Instant.now() },
) {

  fun reserve(
    queue: QueueName,
    workerId: WorkerId,
    leaseDuration: Duration,
  ): RedisReservationResult? {
    require(!leaseDuration.isNegative && !leaseDuration.isZero) {
      "Reservation leaseDuration must be positive."
    }

    val queueKey = RedisKeyspace.queue(queue)
    val jobIdValue = queueAccessor.leftPop(queueKey) ?: return null
    val jobId = JobId(jobIdValue)
    val existingRecord =
      jobRecordStore.find(jobId)
        ?: throw IllegalStateException("Missing job record for reserved job '${jobId.value}'.")
    val now = clock()
    val updatedRecord = existingRecord.copy(bucket = JobBucket.RUNNING, updatedAt = now)
    val lease =
      JobLease(
        jobId = jobId,
        workerId = workerId,
        acquiredAt = now,
        expiresAt = now.plus(leaseDuration),
      )

    jobRecordStore.save(updatedRecord)
    leaseStore.save(lease)

    return RedisReservationResult(
      reservation =
        JobReservation(
          lease = lease,
          payload = updatedRecord.payload,
          reservedAt = now,
        ),
      queueKey = queueKey,
    )
  }
}
