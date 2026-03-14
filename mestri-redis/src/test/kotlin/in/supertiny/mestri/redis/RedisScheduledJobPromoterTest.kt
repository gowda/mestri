package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RedisScheduledJobPromoterTest {
  @Test
  fun `promote due scheduled moves due jobs into queue and leaves future jobs in scheduled`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val listAccessor = InMemoryRedisListAccessor()
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val jobRecordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val scheduledStore =
      RedisScheduledJobStore(
        jobRecordStore = jobRecordStore,
        sortedSetAccessor = sortedSetAccessor,
        clock = { Instant.parse("2026-03-15T12:00:00Z") },
      )
    val promoter =
      RedisScheduledJobPromoter(
        jobRecordStore = jobRecordStore,
        sortedSetAccessor = sortedSetAccessor,
        listAccessor = listAccessor,
      )
    val duePayload = samplePayload("job-due")
    val futurePayload = samplePayload("job-future")

    scheduledStore.persistScheduled(duePayload, Instant.parse("2026-03-15T12:05:00Z"))
    scheduledStore.persistScheduled(futurePayload, Instant.parse("2026-03-15T12:15:00Z"))

    val result = promoter.promoteDueScheduled(now = Instant.parse("2026-03-15T12:10:00Z"))

    assertEquals(listOf(JobId("job-due")), result.promotedJobIds)
    assertEquals(emptyList(), result.skippedJobIds)
    assertEquals(JobBucket.QUEUED, jobRecordStore.find(JobId("job-due"))?.bucket)
    assertEquals(JobBucket.SCHEDULED, jobRecordStore.find(JobId("job-future"))?.bucket)
    assertEquals(
      listOf("job-due"),
      listAccessor.valuesFor(RedisKeyspace.queue(QueueName("billing"))),
    )
    assertEquals(listOf("job-future"), sortedSetAccessor.range(RedisKeyspace.scheduled(), 0, -1))
  }

  @Test
  fun `promote due scheduled skips missing job records and leaves marker for visibility`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val listAccessor = InMemoryRedisListAccessor()
    val sortedSetAccessor = InMemoryRedisSortedSetAccessor()
    val jobRecordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val promoter =
      RedisScheduledJobPromoter(
        jobRecordStore = jobRecordStore,
        sortedSetAccessor = sortedSetAccessor,
        listAccessor = listAccessor,
      )

    sortedSetAccessor.add(
      RedisKeyspace.scheduled(),
      "job-missing",
      Instant.parse("2026-03-15T12:05:00Z").toEpochMilli().toDouble(),
    )

    val result = promoter.promoteDueScheduled(now = Instant.parse("2026-03-15T12:10:00Z"))

    assertEquals(emptyList(), result.promotedJobIds)
    assertEquals(listOf(JobId("job-missing")), result.skippedJobIds)
    assertNull(jobRecordStore.find(JobId("job-missing")))
    assertEquals(listOf("job-missing"), sortedSetAccessor.range(RedisKeyspace.scheduled(), 0, -1))
    assertEquals(emptyList(), listAccessor.valuesFor(RedisKeyspace.queue(QueueName("billing"))))
  }

  private fun samplePayload(jobId: String): JobPayload =
    JobPayload(
      jobId = JobId(jobId),
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

  private class InMemoryRedisListAccessor : RedisListAccessor {
    private val data = mutableMapOf<String, MutableList<String>>()

    override fun rightPush(
      key: String,
      value: String,
    ) {
      data.getOrPut(key) { mutableListOf() }.add(value)
    }

    fun valuesFor(key: String): List<String> = data[key].orEmpty()
  }

  private data class ScoredValue(
    val value: String,
    val score: Double,
  )

  private class InMemoryRedisSortedSetAccessor : RedisSortedSetAccessor {
    private val data = mutableMapOf<String, MutableList<ScoredValue>>()

    override fun add(
      key: String,
      value: String,
      score: Double,
    ) {
      data.getOrPut(key) { mutableListOf() }.add(ScoredValue(value, score))
    }

    override fun range(
      key: String,
      start: Long,
      end: Long,
    ): List<String> =
      data[key]
        .orEmpty()
        .sortedBy { it.score }
        .map { it.value }

    override fun rangeByScore(
      key: String,
      minScoreInclusive: Double,
      maxScoreInclusive: Double,
    ): List<String> =
      data[key]
        .orEmpty()
        .filter { it.score in minScoreInclusive..maxScoreInclusive }
        .sortedBy { it.score }
        .map { it.value }

    override fun remove(
      key: String,
      value: String,
    ) {
      data[key]?.removeAll { it.value == value }
    }
  }
}
