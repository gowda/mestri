package `in`.supertiny.mestri.jobrunr.compat

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.redis.JacksonJobPayloadSerializer
import `in`.supertiny.mestri.redis.RedisHashAccessor
import `in`.supertiny.mestri.redis.RedisImmediateJobEnqueuer
import `in`.supertiny.mestri.redis.RedisJobRecordMapper
import `in`.supertiny.mestri.redis.RedisJobRecordStore
import `in`.supertiny.mestri.redis.RedisListAccessor
import `in`.supertiny.mestri.redis.RedisScheduledJobStore
import `in`.supertiny.mestri.redis.RedisSortedSetAccessor
import org.jobrunr.jobs.lambdas.JobLambda
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import `in`.supertiny.mestri.core.model.JobId as MestriJobId

class MestriJobSchedulerTest {
  @Test
  fun `enqueue persists queued payload and returns compat job id`() {
    val hashAccessor = InMemoryHashAccessor()
    val listAccessor = InMemoryListAccessor()
    val sortedSetAccessor = InMemorySortedSetAccessor()
    val recordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val enqueuer =
      RedisImmediateJobEnqueuer(
        jobRecordStore = recordStore,
        listAccessor = listAccessor,
      )
    val scheduledStore =
      RedisScheduledJobStore(
        jobRecordStore = recordStore,
        sortedSetAccessor = sortedSetAccessor,
      )
    val scheduler =
      MestriJobScheduler(
        immediateJobEnqueuer = enqueuer,
        scheduledJobStore = scheduledStore,
        defaultQueue = QueueName("billing"),
        jobIdGenerator = { MestriJobId("job-compat-1") },
      )

    val jobId = scheduler.enqueue(sampleJobLambda())
    val storedRecord = recordStore.find(MestriJobId("job-compat-1"))

    assertEquals("job-compat-1", jobId.value)
    assertEquals(listOf("job-compat-1"), listAccessor.values("mestri:queue:billing"))
    assertNotNull(storedRecord)
    assertEquals(JobBucket.QUEUED, storedRecord.bucket)
    assertEquals(QueueName("billing"), storedRecord.payload.queue)
    assertEquals(setOf("compat"), storedRecord.payload.labels)
  }

  @Test
  fun `schedule persists scheduled payload and returns compat job id`() {
    val hashAccessor = InMemoryHashAccessor()
    val listAccessor = InMemoryListAccessor()
    val sortedSetAccessor = InMemorySortedSetAccessor()
    val recordStore =
      RedisJobRecordStore(
        hashAccessor = hashAccessor,
        mapper = RedisJobRecordMapper(JacksonJobPayloadSerializer()),
      )
    val enqueuer =
      RedisImmediateJobEnqueuer(
        jobRecordStore = recordStore,
        listAccessor = listAccessor,
      )
    val scheduledStore =
      RedisScheduledJobStore(
        jobRecordStore = recordStore,
        sortedSetAccessor = sortedSetAccessor,
      )
    val scheduler =
      MestriJobScheduler(
        immediateJobEnqueuer = enqueuer,
        scheduledJobStore = scheduledStore,
        defaultQueue = QueueName("billing"),
        jobIdGenerator = { MestriJobId("job-compat-2") },
      )

    val jobId = scheduler.schedule(Instant.parse("2026-03-15T14:00:00Z"), sampleJobLambda())
    val storedRecord = recordStore.find(MestriJobId("job-compat-2"))

    assertEquals("job-compat-2", jobId.value)
    assertEquals(1, sortedSetAccessor.range("mestri:scheduled", 0, 10).size)
    assertEquals("job-compat-2", sortedSetAccessor.range("mestri:scheduled", 0, 10).single())
    assertNotNull(storedRecord)
    assertEquals(JobBucket.SCHEDULED, storedRecord.bucket)
    assertEquals(QueueName("billing"), storedRecord.payload.queue)
  }

  private fun sampleJobLambda(): JobLambda =
    JobLambda {
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
        labels = setOf("compat"),
      )
    }

  private class InMemoryHashAccessor : RedisHashAccessor {
    private val values = mutableMapOf<String, MutableMap<String, String>>()

    override fun putAll(
      key: String,
      fields: Map<String, String>,
    ) {
      values[key] = fields.toMutableMap()
    }

    override fun entries(key: String): Map<String, String> = values[key].orEmpty()

    override fun delete(key: String) {
      values.remove(key)
    }
  }

  private class InMemoryListAccessor : RedisListAccessor {
    private val values = linkedMapOf<String, MutableList<String>>()

    override fun rightPush(
      key: String,
      value: String,
    ) {
      values.getOrPut(key) { mutableListOf() }.add(value)
    }

    fun values(key: String): List<String> = values[key].orEmpty()
  }

  private class InMemorySortedSetAccessor : RedisSortedSetAccessor {
    private val values = linkedMapOf<String, MutableMap<String, Double>>()

    override fun add(
      key: String,
      value: String,
      score: Double,
    ) {
      values.getOrPut(key) { linkedMapOf() }[value] = score
    }

    override fun range(
      key: String,
      start: Long,
      end: Long,
    ): List<String> =
      values[key]
        .orEmpty()
        .entries
        .sortedBy { (_, score) -> score }
        .map { (value, _) -> value }
        .let { ranked ->
          if (ranked.isEmpty() || start > ranked.lastIndex) {
            emptyList()
          } else {
            ranked.subList(start.toInt(), (end.toInt().coerceAtMost(ranked.lastIndex)) + 1)
          }
        }

    override fun rangeByScore(
      key: String,
      minScoreInclusive: Double,
      maxScoreInclusive: Double,
    ): List<String> =
      values[key]
        .orEmpty()
        .entries
        .filter { (_, score) -> score in minScoreInclusive..maxScoreInclusive }
        .sortedBy { (_, score) -> score }
        .map { (value, _) -> value }

    override fun remove(
      key: String,
      value: String,
    ) {
      values[key]?.remove(value)
    }
  }
}
