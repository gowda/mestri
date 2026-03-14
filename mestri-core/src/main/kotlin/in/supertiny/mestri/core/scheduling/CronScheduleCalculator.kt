package `in`.supertiny.mestri.core.scheduling

import `in`.supertiny.mestri.core.model.RecurringJobDefinition
import `in`.supertiny.mestri.core.model.RecurringSchedule
import org.springframework.scheduling.support.CronExpression
import java.time.Instant

object CronScheduleCalculator {
  fun validate(schedule: RecurringSchedule) {
    parse(schedule)
  }

  fun nextRunAt(
    schedule: RecurringSchedule,
    from: Instant,
  ): Instant? =
    parse(schedule)
      .next(from.atZone(schedule.zoneId))
      ?.toInstant()

  fun nextRunAt(
    definition: RecurringJobDefinition,
    from: Instant,
  ): Instant? = nextRunAt(definition.schedule, from)

  private fun parse(schedule: RecurringSchedule): CronExpression =
    try {
      CronExpression.parse(toSpringCronExpression(schedule.cronExpression))
    } catch (exception: IllegalArgumentException) {
      throw IllegalArgumentException(
        "Invalid recurring cron expression '${schedule.cronExpression}'.",
        exception,
      )
    }

  private fun toSpringCronExpression(cronExpression: String): String {
    val fields = cronExpression.trim().split(Regex("\\s+"))
    return when (fields.size) {
      5 -> "0 $cronExpression"
      6 -> cronExpression
      else ->
        throw IllegalArgumentException(
          "Recurring cron expression must have 5 or 6 fields.",
        )
    }
  }
}
