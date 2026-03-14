package `in`.supertiny.mestri.core.model

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecurringJobDefinitionTest {
  @Test
  fun `recurring job definition captures schedule invocation and lifecycle timestamps`() {
    val createdAt = Instant.parse("2026-03-15T12:00:00Z")
    val updatedAt = Instant.parse("2026-03-15T12:05:00Z")
    val nextRunAt = Instant.parse("2026-03-15T13:00:00Z")

    val definition =
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
            cronExpression = "0 0 * * *",
            zoneId = ZoneId.of("Asia/Kolkata"),
          ),
        labels = setOf("billing", "nightly"),
        metadata = mapOf("tenant" to "tenant-1"),
        deduplicationKey = DeduplicationKey("nightly-billing:tenant-1"),
        deduplicationPolicy = DeduplicationPolicy(scope = DeduplicationScope.SCHEDULE),
        createdAt = createdAt,
        updatedAt = updatedAt,
        nextRunAt = nextRunAt,
      )

    assertEquals(RecurringJobId("nightly-billing"), definition.id)
    assertEquals("0 0 * * *", definition.schedule.cronExpression)
    assertEquals(ZoneId.of("Asia/Kolkata"), definition.schedule.zoneId)
    assertEquals(nextRunAt, definition.nextRunAt)
    assertEquals(true, definition.enabled)
  }

  @Test
  fun `recurring schedule rejects blank cron expression`() {
    assertFailsWith<IllegalArgumentException> {
      RecurringSchedule(cronExpression = "")
    }
  }

  @Test
  fun `recurring definition rejects blank metadata values and invalid timestamps`() {
    val createdAt = Instant.parse("2026-03-15T12:00:00Z")

    assertFailsWith<IllegalArgumentException> {
      RecurringJobDefinition(
        id = RecurringJobId("nightly-billing"),
        invocation = sampleInvocation(),
        queue = QueueName("billing"),
        schedule = RecurringSchedule(cronExpression = "0 0 * * *"),
        metadata = mapOf("tenant" to ""),
        createdAt = createdAt,
        updatedAt = createdAt,
      )
    }

    assertFailsWith<IllegalArgumentException> {
      RecurringJobDefinition(
        id = RecurringJobId("nightly-billing"),
        invocation = sampleInvocation(),
        queue = QueueName("billing"),
        schedule = RecurringSchedule(cronExpression = "0 0 * * *"),
        deduplicationPolicy = DeduplicationPolicy(scope = DeduplicationScope.SCHEDULE),
        createdAt = createdAt,
        updatedAt = createdAt,
      )
    }

    assertFailsWith<IllegalArgumentException> {
      RecurringJobDefinition(
        id = RecurringJobId("nightly-billing"),
        invocation = sampleInvocation(),
        queue = QueueName("billing"),
        schedule = RecurringSchedule(cronExpression = "0 0 * * *"),
        createdAt = createdAt,
        updatedAt = Instant.parse("2026-03-15T11:59:59Z"),
      )
    }
  }

  private fun sampleInvocation(): InvocationDescriptor =
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
    )
}
