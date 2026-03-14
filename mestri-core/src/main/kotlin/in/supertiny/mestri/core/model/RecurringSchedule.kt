package `in`.supertiny.mestri.core.model

import java.time.ZoneId

data class RecurringSchedule(
  val cronExpression: String,
  val zoneId: ZoneId = ZoneId.of("UTC"),
) {
  init {
    require(cronExpression.isNotBlank()) { "Recurring cronExpression must not be blank." }
  }
}
