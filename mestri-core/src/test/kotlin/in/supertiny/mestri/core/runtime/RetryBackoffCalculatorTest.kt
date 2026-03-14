package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.RetryPolicy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RetryBackoffCalculatorTest {

  @Test
  fun `retry backoff grows exponentially without jitter`() {
    val calculator = RetryBackoffCalculator()
    val policy =
      RetryPolicy(
        initialDelay = 5.seconds,
        maxDelay = 5.minutes,
        backoffMultiplier = 2.0,
        useJitter = false,
      )

    assertEquals(5.seconds, calculator.nextDelay(policy, attempt = 1))
    assertEquals(10.seconds, calculator.nextDelay(policy, attempt = 2))
    assertEquals(20.seconds, calculator.nextDelay(policy, attempt = 3))
  }

  @Test
  fun `retry backoff respects max delay`() {
    val calculator = RetryBackoffCalculator()
    val policy =
      RetryPolicy(
        initialDelay = 30.seconds,
        maxDelay = 1.minutes,
        backoffMultiplier = 3.0,
        useJitter = false,
      )

    assertEquals(1.minutes, calculator.nextDelay(policy, attempt = 3))
  }

  @Test
  fun `retry backoff applies deterministic jitter when enabled`() {
    val calculator = RetryBackoffCalculator(random = Random(1234))
    val policy =
      RetryPolicy(
        initialDelay = 10.seconds,
        maxDelay = 1.minutes,
        backoffMultiplier = 2.0,
        useJitter = true,
      )
    val delay = calculator.nextDelay(policy, attempt = 1)

    assertTrue(delay >= 5.seconds)
    assertTrue(delay < 10.seconds)
  }

  @Test
  fun `retry backoff rejects non positive attempts`() {
    val calculator = RetryBackoffCalculator()

    assertFailsWith<IllegalArgumentException> {
      calculator.nextDelay(RetryPolicy.DEFAULT, attempt = 0)
    }
  }
}
