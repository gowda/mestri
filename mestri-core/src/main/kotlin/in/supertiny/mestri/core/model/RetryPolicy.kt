package in.supertiny.mestri.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

data class RetryPolicy(
  val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
  val initialDelay: Duration = DEFAULT_INITIAL_DELAY,
  val maxDelay: Duration = DEFAULT_MAX_DELAY,
  val backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
  val useJitter: Boolean = true,
) {
  init {
    require(maxAttempts >= 0) { "Retry maxAttempts must be zero or greater." }
    require(initialDelay.isPositive()) { "Retry initialDelay must be positive." }
    require(maxDelay >= initialDelay) { "Retry maxDelay must be greater than or equal to initialDelay." }
    require(backoffMultiplier >= 1.0) { "Retry backoffMultiplier must be at least 1.0." }
  }

  companion object {
    const val DEFAULT_MAX_ATTEMPTS: Int = 25
    val DEFAULT_INITIAL_DELAY: Duration = 5.seconds
    val DEFAULT_MAX_DELAY: Duration = 1.hours
    const val DEFAULT_BACKOFF_MULTIPLIER: Double = 2.0

    val NONE: RetryPolicy = RetryPolicy(maxAttempts = 0)
    val DEFAULT: RetryPolicy = RetryPolicy()
  }
}
