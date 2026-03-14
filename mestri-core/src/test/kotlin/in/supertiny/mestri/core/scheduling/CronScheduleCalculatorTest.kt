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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CronScheduleCalculatorTest {
  @Test
  fun `validate accepts five field cron expressions`() {
    val schedule =
      RecurringSchedule(
        cronExpression = "0 0 * * *",
        zoneId = ZoneId.of("UTC"),
      )

    CronScheduleCalculator.validate(schedule)
  }

  @Test
  fun `validate rejects malformed cron expressions`() {
    val schedule =
      RecurringSchedule(
        cronExpression = "not-a-cron",
        zoneId = ZoneId.of("UTC"),
      )

    assertFailsWith<IllegalArgumentException> {
      CronScheduleCalculator.validate(schedule)
    }
  }

  @Test
  fun `next run calculation respects schedule timezone`() {
    val schedule =
      RecurringSchedule(
        cronExpression = "0 9 * * *",
        zoneId = ZoneId.of("Asia/Kolkata"),
      )

    val nextRunAt =
      CronScheduleCalculator.nextRunAt(
        schedule = schedule,
        from = Instant.parse("2026-03-15T03:20:00Z"),
      )

    assertEquals(Instant.parse("2026-03-15T03:30:00Z"), nextRunAt)
  }

  @Test
  fun `next run calculation supports recurring job definition`() {
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
            cronExpression = "30 * * * *",
            zoneId = ZoneId.of("UTC"),
          ),
        createdAt = Instant.parse("2026-03-15T12:00:00Z"),
        updatedAt = Instant.parse("2026-03-15T12:00:00Z"),
      )

    val nextRunAt =
      CronScheduleCalculator.nextRunAt(
        definition = definition,
        from = Instant.parse("2026-03-15T12:10:00Z"),
      )

    assertNotNull(nextRunAt)
    assertEquals(Instant.parse("2026-03-15T12:30:00Z"), nextRunAt)
  }
}
