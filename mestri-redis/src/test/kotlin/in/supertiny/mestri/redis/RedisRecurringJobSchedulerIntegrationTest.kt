package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RecurringJobDefinition
import `in`.supertiny.mestri.core.model.RecurringJobId
import `in`.supertiny.mestri.core.model.RecurringSchedule
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.scheduling.RecurringJobScheduler
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class RedisRecurringJobSchedulerIntegrationTest : RedisIntegrationSupport() {
  @Test
  fun `recurring scheduler stores timezone aware next run and advances after enqueue`() {
    val redisTemplate = newStringRedisTemplate()
    flushRedis(redisTemplate)
    val store =
      RedisRecurringJobDefinitionStore(
        hashAccessor = newHashAccessor(redisTemplate),
        setAccessor = newSetAccessor(redisTemplate),
      )
    val scheduler = RecurringJobScheduler(store)
    val definition = sampleDefinition()

    val registered =
      scheduler.register(
        definition = definition,
        now = Instant.parse("2026-03-15T03:20:00Z"),
      )
    val dueDefinitions = scheduler.dueDefinitions(now = Instant.parse("2026-03-15T03:30:00Z"))
    val updated =
      scheduler.markEnqueued(
        definition = dueDefinitions.single(),
        enqueuedAt = Instant.parse("2026-03-15T03:30:00Z"),
      )

    assertEquals(Instant.parse("2026-03-15T03:30:00Z"), registered.nextRunAt)
    assertEquals(listOf(RecurringJobId("nightly-billing")), dueDefinitions.map { it.id })
    assertEquals(Instant.parse("2026-03-15T03:30:00Z"), updated.lastEnqueuedAt)
    assertEquals(Instant.parse("2026-03-16T03:30:00Z"), updated.nextRunAt)
    assertEquals(updated, store.find(updated.id))
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
    )
}
