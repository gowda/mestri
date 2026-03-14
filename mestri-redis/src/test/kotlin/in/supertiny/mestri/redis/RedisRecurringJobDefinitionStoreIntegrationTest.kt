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

class RedisRecurringJobDefinitionStoreIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `save and find recurring definition in real redis`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)
    val store =
      RedisRecurringJobDefinitionStore(
        hashAccessor = newHashAccessor(redisTemplate),
        setAccessor = newSetAccessor(redisTemplate),
      )
    val definition = sampleDefinition()

    store.save(definition)

    assertEquals(definition, store.find(definition.id))
    assertEquals(listOf(definition), store.findAll())
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
}
