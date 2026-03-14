package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RecurringJobDefinition
import `in`.supertiny.mestri.core.model.RecurringJobId
import `in`.supertiny.mestri.core.model.RecurringSchedule
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RedisRecurringJobDefinitionStoreTest {
  @Test
  fun `save and find round trips recurring definition`() {
    val hashAccessor = InMemoryRedisHashAccessor()
    val setAccessor = InMemoryRedisSetAccessor()
    val store = RedisRecurringJobDefinitionStore(hashAccessor, setAccessor)
    val definition = sampleDefinition()

    store.save(definition)

    assertEquals(definition, store.find(definition.id))
    assertEquals(listOf(definition), store.findAll())
  }

  @Test
  fun `find returns null for missing recurring definition`() {
    val store =
      RedisRecurringJobDefinitionStore(
        InMemoryRedisHashAccessor(),
        InMemoryRedisSetAccessor(),
      )

    assertNull(store.find(RecurringJobId("missing-job")))
  }

  private fun sampleDefinition(): RecurringJobDefinition =
    RecurringJobDefinition(
      id = RecurringJobId("nightly-billing"),
      invocation =
        InvocationDescriptor(
          target =
            SpringBeanTarget(
              className = "io.example.jobs.BillingJobs",
              beanName = "billingJobs",
            ),
          method =
            InvocationMethod(
              name = "runNightlyBilling",
              parameterTypeNames = listOf("java.lang.String"),
            ),
          arguments = listOf(InvocationArgument("java.lang.String", "\"tenant-1\"")),
        ),
      queue = QueueName("billing"),
      schedule =
        RecurringSchedule(
          cronExpression = "0 9 * * *",
          zoneId = ZoneId.of("Asia/Kolkata"),
        ),
      createdAt = Instant.parse("2026-03-15T03:00:00Z"),
      updatedAt = Instant.parse("2026-03-15T03:00:00Z"),
      nextRunAt = Instant.parse("2026-03-16T03:30:00Z"),
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

  private class InMemoryRedisSetAccessor : RedisSetAccessor {
    private val data = mutableMapOf<String, MutableSet<String>>()

    override fun add(
      key: String,
      value: String,
    ) {
      data.getOrPut(key) { linkedSetOf() }.add(value)
    }

    override fun members(key: String): Set<String> = data[key].orEmpty()

    override fun remove(
      key: String,
      value: String,
    ) {
      data[key]?.remove(value)
    }
  }
}
