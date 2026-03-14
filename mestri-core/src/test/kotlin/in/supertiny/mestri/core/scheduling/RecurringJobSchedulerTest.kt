package `in`.supertiny.mestri.core.scheduling

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

class RecurringJobSchedulerTest {
  @Test
  fun `register computes next run using schedule timezone`() {
    val store = InMemoryRecurringJobDefinitionStore()
    val scheduler = RecurringJobScheduler(store)
    val registered =
      scheduler.register(
        definition = sampleDefinition(),
        now = Instant.parse("2026-03-15T03:20:00Z"),
      )

    assertEquals(Instant.parse("2026-03-15T03:30:00Z"), registered.nextRunAt)
    assertEquals(registered, store.findAll().single())
  }

  @Test
  fun `due definitions returns enabled recurring jobs that are due`() {
    val store = InMemoryRecurringJobDefinitionStore()
    val scheduler = RecurringJobScheduler(store)
    store.save(
      sampleDefinition(id = "job-due").copy(
        nextRunAt = Instant.parse("2026-03-15T03:30:00Z"),
      ),
    )
    store.save(
      sampleDefinition(id = "job-future").copy(
        nextRunAt = Instant.parse("2026-03-15T04:30:00Z"),
      ),
    )
    store.save(
      sampleDefinition(id = "job-disabled").copy(
        enabled = false,
        nextRunAt = Instant.parse("2026-03-15T03:00:00Z"),
      ),
    )

    val dueDefinitions = scheduler.dueDefinitions(now = Instant.parse("2026-03-15T03:30:00Z"))

    assertEquals(listOf(RecurringJobId("job-due")), dueDefinitions.map { it.id })
  }

  @Test
  fun `mark enqueued updates last enqueued at and schedules next run`() {
    val store = InMemoryRecurringJobDefinitionStore()
    val scheduler = RecurringJobScheduler(store)
    val definition =
      sampleDefinition().copy(
        nextRunAt = Instant.parse("2026-03-15T03:30:00Z"),
      )

    val updatedDefinition =
      scheduler.markEnqueued(
        definition = definition,
        enqueuedAt = Instant.parse("2026-03-15T03:30:00Z"),
      )

    assertEquals(Instant.parse("2026-03-15T03:30:00Z"), updatedDefinition.lastEnqueuedAt)
    assertEquals(Instant.parse("2026-03-16T03:30:00Z"), updatedDefinition.nextRunAt)
    assertEquals(updatedDefinition, store.findAll().single())
  }

  @Test
  fun `register clears next run for disabled recurring definitions`() {
    val store = InMemoryRecurringJobDefinitionStore()
    val scheduler = RecurringJobScheduler(store)

    val registered =
      scheduler.register(
        definition = sampleDefinition().copy(enabled = false),
        now = Instant.parse("2026-03-15T03:20:00Z"),
      )

    assertNull(registered.nextRunAt)
  }

  private fun sampleDefinition(id: String = "nightly-billing"): RecurringJobDefinition =
    RecurringJobDefinition(
      id = RecurringJobId(id),
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

  private class InMemoryRecurringJobDefinitionStore : RecurringJobDefinitionStore {
    private val definitions = linkedMapOf<RecurringJobId, RecurringJobDefinition>()

    override fun save(definition: RecurringJobDefinition) {
      definitions[definition.id] = definition
    }

    override fun find(id: RecurringJobId): RecurringJobDefinition? = definitions[id]

    override fun findAll(): List<RecurringJobDefinition> = definitions.values.toList()
  }
}
