package `in`.supertiny.mestri.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class DeduplicationScope {
  ENQUEUE,
  SCHEDULE,
}

enum class DeduplicationStrategy {
  REJECT,
  COALESCE,
}

data class DeduplicationPolicy(
  val scope: DeduplicationScope,
  val strategy: DeduplicationStrategy = DeduplicationStrategy.REJECT,
  val ttl: Duration = DEFAULT_TTL,
) {
  init {
    require(ttl.isPositive()) { "Deduplication ttl must be positive." }
  }

  companion object {
    val DEFAULT_TTL: Duration = 15.minutes
  }
}
