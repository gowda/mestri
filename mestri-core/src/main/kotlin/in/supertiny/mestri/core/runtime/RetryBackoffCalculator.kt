package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.RetryPolicy
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration

class RetryBackoffCalculator(
  private val random: Random = Random.Default,
) {

  fun nextDelay(
    policy: RetryPolicy,
    attempt: Int,
  ): Duration {
    require(attempt > 0) { "Retry attempt must be positive." }

    val exponentialMultiplier = policy.backoffMultiplier.pow((attempt - 1).toDouble())
    val baseDelay = policy.initialDelay * exponentialMultiplier
    val boundedDelay = if (baseDelay > policy.maxDelay) policy.maxDelay else baseDelay

    if (!policy.useJitter) {
      return boundedDelay
    }

    val jitterMultiplier = random.nextDouble(from = 0.5, until = 1.0)
    return boundedDelay * jitterMultiplier
  }
}
