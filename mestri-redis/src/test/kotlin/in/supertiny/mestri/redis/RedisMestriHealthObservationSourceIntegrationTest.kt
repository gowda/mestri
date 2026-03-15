package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.JobLease
import `in`.supertiny.mestri.core.runtime.ProcessId
import `in`.supertiny.mestri.core.runtime.WorkerHeartbeat
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RedisMestriHealthObservationSourceIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `observation source reports queue depth worker liveness and stale lease checks`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)

    val jobRecordStore = newJobRecordStore(redisTemplate)
    val leaseStore = newLeaseStore(redisTemplate)
    val heartbeatStore = newHeartbeatStore(redisTemplate)
    val clock =
      Clock.fixed(
        Instant.parse("2026-03-15T12:01:00Z"),
        ZoneOffset.UTC,
      )

    val enqueuer =
      RedisImmediateJobEnqueuer(
        jobRecordStore = jobRecordStore,
        listAccessor = SpringRedisListAccessor(redisTemplate),
        clock = { Instant.parse("2026-03-15T12:00:00Z") },
      )
    val scheduledStore =
      RedisScheduledJobStore(
        jobRecordStore = jobRecordStore,
        sortedSetAccessor = SpringRedisSortedSetAccessor(redisTemplate),
        clock = { Instant.parse("2026-03-15T11:59:00Z") },
      )

    enqueuer.enqueue(samplePayload(jobId = JobId("job-queued")))
    scheduledStore.persistRetry(
      payload = samplePayload(jobId = JobId("job-retry")),
      dueAt = Instant.parse("2026-03-15T12:05:00Z"),
    )
    heartbeatStore.save(
      WorkerHeartbeat(
        workerId = WorkerId("worker-active"),
        processId = ProcessId("process-active"),
        queues = setOf("billing"),
        recordedAt = Instant.parse("2026-03-15T12:00:55Z"),
        expiresAt = Instant.parse("2026-03-15T12:01:30Z"),
      ),
    )
    heartbeatStore.save(
      WorkerHeartbeat(
        workerId = WorkerId("worker-stale"),
        processId = ProcessId("process-stale"),
        queues = setOf("billing"),
        recordedAt = Instant.parse("2026-03-15T11:55:00Z"),
        expiresAt = Instant.parse("2026-03-15T12:00:00Z"),
      ),
    )
    leaseStore.save(
      JobLease(
        jobId = JobId("job-lease"),
        workerId = WorkerId("worker-stale"),
        acquiredAt = Instant.parse("2026-03-15T11:58:00Z"),
        expiresAt = Instant.parse("2026-03-15T12:00:30Z"),
      ),
    )

    val observationSource =
      RedisMestriHealthObservationSource(
        redisTemplate = redisTemplate,
        jobRecordStore = jobRecordStore,
        leaseStore = leaseStore,
        heartbeatStore = heartbeatStore,
        queues = listOf(QueueName("billing")),
        schedulerEnabled = true,
        promoterEnabled = true,
        clock = clock,
      )

    val observation = observationSource.observe()
    val queueDepth = observation.queues.single()
    val oldestJobAge = queueDepth.oldestJobAge

    assertEquals(true, observation.redis.reachable)
    assertEquals(1, observation.workers.activeWorkers)
    assertEquals(1, observation.workers.staleWorkers)
    assertEquals(1, observation.workers.staleLeases)
    assertEquals(1L, queueDepth.enqueuedJobs)
    assertNotNull(oldestJobAge)
    assertTrue(oldestJobAge >= Duration.ofMinutes(1))
    assertEquals(1L, observation.buckets.retryJobs)
    assertEquals(true, observation.scheduler.schedulerEnabled)
    assertEquals(true, observation.scheduler.promoterEnabled)
    assertEquals("redis", observation.details["healthSource"])
  }

  private fun samplePayload(jobId: JobId): JobPayload =
    JobPayload(
      jobId = jobId,
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
          arguments = listOf(InvocationArgument("java.lang.String", "\"invoice-42\"")),
        ),
      queue = QueueName("billing"),
    )
}
