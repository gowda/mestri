package `in`.supertiny.mestri.core.model

@JvmInline
value class RecurringJobId(
  val value: String,
) {
  init {
    require(value.isNotBlank()) { "Recurring job id must not be blank." }
  }
}
