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
import kotlin.test.assertNull

class RedisQueueReservationStoreTest {

  @Test
  fun `reserve moves queued job to running with lease`() {
    val redis = InMemoryRedis()
    val recordStore = recordStore(redis)
    val leaseStore = RedisJobLeaseStore(redis)
    val enqueuer =
      RedisImmediateJobEnqueuer(
        jobRecordStore = recordStore,
        listAccessor = redis,
        clock = { Instant.parse("2026-03-15T10:00:00Z") },
      )
    val reservationStore =
      RedisQueueReservationStore(
        queueAccessor = redis,
        jobRecordStore = recordStore,
        leaseStore = leaseStore,
        clock = { Instant.parse("2026-03-15T10:01:00Z") },
      )
    val payload = samplePayload()

    enqueuer.enqueue(payload)
    val result =
      reservationStore.reserve(
        queue = payload.queue,
        workerId = WorkerId("worker-1"),
        leaseDuration = Duration.ofMinutes(5),
      )

    assertNotNull(result)
    assertEquals("mestri:queue:billing", result.queueKey)
    assertEquals(payload.jobId, result.reservation.payload.jobId)
    assertEquals(WorkerId("worker-1"), result.reservation.lease.workerId)
    assertEquals(
      Instant.parse("2026-03-15T10:06:00Z"),
      result.reservation.lease.expiresAt,
    )
    assertEquals(JobBucket.RUNNING, recordStore.find(payload.jobId)?.bucket)
    assertNotNull(leaseStore.find(payload.jobId))
    assertEquals(emptyList(), redis.listValues("mestri:queue:billing"))
  }

  @Test
  fun `reserve returns null for empty queue`() {
    val redis = InMemoryRedis()
    val reservationStore =
      RedisQueueReservationStore(
        queueAccessor = redis,
        jobRecordStore = recordStore(redis),
        leaseStore = RedisJobLeaseStore(redis),
      )

    val result =
      reservationStore.reserve(
        queue = QueueName("billing"),
        workerId = WorkerId("worker-1"),
        leaseDuration = Duration.ofMinutes(5),
      )

    assertNull(result)
  }

  private fun recordStore(redis: InMemoryRedis): RedisJobRecordStore =
    RedisJobRecordStore(
      hashAccessor = redis,
      mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
    )

  private fun samplePayload(): JobPayload =
    JobPayload(
      jobId = JobId("job-123"),
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

  private class InMemoryRedis : RedisHashAccessor, RedisQueueAccessor {
    private val hashes = mutableMapOf<String, Map<String, String>>()
    private val lists = mutableMapOf<String, MutableList<String>>()

    override fun entries(key: String): Map<String, String> = hashes[key].orEmpty()

    override fun putAll(key: String, fields: Map<String, String>) {
      hashes[key] = fields.toMap()
    }

    override fun rightPush(key: String, value: String) {
      lists.getOrPut(key) { mutableListOf() }.add(value)
    }

    override fun leftPop(key: String): String? = lists[key]?.removeFirstOrNull()

    fun listValues(key: String): List<String> = lists[key].orEmpty()
  }
}
