package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.middleware.ServerExecutionResult
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
import kotlin.test.assertNull

class RedisSuccessfulExecutionIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `successful execution persists succeeded record and removes lease in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val jobRecordStore = newJobRecordStore(redisTemplate)
    val leaseStore = newLeaseStore(redisTemplate)
    val payload = samplePayload()
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
        leaseStore = leaseStore,
        clock = { Instant.parse("2026-03-15T12:01:00Z") },
      )
    val successHandler =
      RedisSuccessfulExecutionHandler(
        terminalJobStore =
          RedisTerminalJobStore(
            jobRecordStore = jobRecordStore,
            sortedSetAccessor = newSortedSetAccessor(redisTemplate),
          ),
        leaseStore = leaseStore,
        clock = { Instant.parse("2026-03-15T12:02:00Z") },
      )

    enqueuer.enqueue(payload)
    val reservation =
      reservationStore
        .reserve(
          queue = payload.queue,
          workerId = WorkerId("worker-1"),
          leaseDuration = Duration.ofMinutes(5),
        )?.reservation

    assertNotNull(reservation)

    successHandler.handle(
      reservation = reservation,
      result =
        ServerExecutionResult(
          outcome = ServerExecutionOutcome.SUCCEEDED,
          metadata = mapOf("traceId" to "trace-1"),
        ),
    )

    val updatedRecord = jobRecordStore.find(payload.jobId)
    val lease = leaseStore.find(payload.jobId)
    val succeededScore =
      redisTemplate.opsForZSet().score(RedisKeyspace.succeeded(), payload.jobId.value)

    assertNotNull(updatedRecord)
    assertEquals(JobBucket.SUCCEEDED, updatedRecord.bucket)
    assertEquals("trace-1", updatedRecord.payload.metadata["traceId"])
    assertNull(lease)
    assertEquals(Instant.parse("2026-03-15T12:02:00Z").toEpochMilli().toDouble(), succeededScore)
  }

  private fun samplePayload(): JobPayload =
    JobPayload(
      jobId = JobId("job-success-1"),
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
      metadata = mapOf("requestId" to "request-1"),
    )
}
