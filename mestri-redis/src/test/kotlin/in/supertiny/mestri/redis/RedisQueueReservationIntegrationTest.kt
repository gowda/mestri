package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RedisQueueReservationIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `reservation pops queued job and creates running lease in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val jobRecordStore = newJobRecordStore(redisTemplate)
    val enqueuer =
      RedisImmediateJobEnqueuer(
        jobRecordStore = jobRecordStore,
        listAccessor = SpringRedisListAccessor(redisTemplate),
        clock = { Instant.parse("2026-03-15T12:00:00Z") },
      )
    val reservationStore =
      RedisQueueReservationStore(
        queueAccessor = SpringRedisQueueAccessor(redisTemplate),
        jobRecordStore = jobRecordStore,
        leaseStore = newLeaseStore(redisTemplate),
        clock = { Instant.parse("2026-03-15T12:01:00Z") },
      )
    val payload = samplePayload()

    enqueuer.enqueue(payload)

    val result =
      reservationStore.reserve(
        queue = payload.queue,
        workerId = WorkerId("worker-1"),
        leaseDuration = Duration.ofMinutes(5),
      )
    val queueValues =
      redisTemplate.opsForList().range(RedisKeyspace.queue(payload.queue), 0, -1).orEmpty()
    val updatedRecord = jobRecordStore.find(payload.jobId)
    val lease = newLeaseStore(redisTemplate).find(payload.jobId)

    assertNotNull(result)
    assertEquals(payload.jobId, result.reservation.payload.jobId)
    assertEquals(WorkerId("worker-1"), result.reservation.lease.workerId)
    assertEquals(Instant.parse("2026-03-15T12:06:00Z"), result.reservation.lease.expiresAt)
    assertEquals(emptyList(), queueValues)

    assertNotNull(updatedRecord)
    assertEquals(JobBucket.RUNNING, updatedRecord.bucket)

    assertNotNull(lease)
    assertEquals(WorkerId("worker-1"), lease.workerId)
  }

  private fun samplePayload(): JobPayload =
    JobPayload(
      jobId = JobId("job-reservation-1"),
      invocation =
        InvocationDescriptor(
          target =
            SpringBeanTarget(
              className = "io.example.jobs.InvoiceJobs",
              beanName = "invoiceJobs",
            ),
          method =
            InvocationMethod(
              name = "generateInvoice",
              parameterTypeNames = listOf("java.lang.String"),
            ),
          arguments = listOf(InvocationArgument("java.lang.String", "\"user-1\"")),
        ),
      queue = QueueName("billing"),
    )
}
