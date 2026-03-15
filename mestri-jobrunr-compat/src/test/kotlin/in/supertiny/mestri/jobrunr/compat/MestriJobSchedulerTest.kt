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
import org.jobrunr.jobs.lambdas.JobLambda
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import `in`.supertiny.mestri.core.model.JobId as MestriJobId

class MestriJobSchedulerTest {
  @Test
  fun `enqueue persists queued payload and returns compat job id`() {
    val hashAccessor = InMemoryHashAccessor()
    val listAccessor = InMemoryListAccessor()
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
    val scheduler =
      MestriJobScheduler(
        immediateJobEnqueuer = enqueuer,
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
}
