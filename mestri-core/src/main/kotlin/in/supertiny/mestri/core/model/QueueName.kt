package `in`.supertiny.mestri.core.model

@JvmInline
value class QueueName(
  val value: String,
) {
  init {
    require(value.isNotBlank()) { "Queue name must not be blank." }
    require(value.length <= MAX_LENGTH) { "Queue name must be at most $MAX_LENGTH characters." }
    require(ALLOWED_PATTERN.matches(value)) {
      "Queue name may only contain letters, numbers, underscores, hyphens, colons, and periods."
    }
  }

  companion object {
    const val DEFAULT_VALUE: String = "default"
    private const val MAX_LENGTH: Int = 128
    private val ALLOWED_PATTERN = Regex("[A-Za-z0-9_.:-]+")

    val DEFAULT: QueueName = QueueName(DEFAULT_VALUE)
  }
}
