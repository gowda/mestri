package in.supertiny.mestri.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class CoreDomainTypesTest {

  @Test
  fun `job id random creates non blank id`() {
    val jobId = JobId.random()

    assertTrue(jobId.value.isNotBlank())
  }

  @Test
  fun `queue name rejects unsupported characters`() {
    assertFailsWith<IllegalArgumentException> {
      QueueName("billing queue")
    }
  }

  @Test
  fun `retry policy none disables retries`() {
    assertEquals(0, RetryPolicy.NONE.maxAttempts)
  }

  @Test
  fun `retry policy rejects max delay smaller than initial delay`() {
    assertFailsWith<IllegalArgumentException> {
      RetryPolicy(initialDelay = 10.seconds, maxDelay = 5.seconds)
    }
  }

  @Test
  fun `retention policy falls back to global retention`() {
    val policy = RetentionPolicy(global = 14.days, succeeded = 2.days)

    assertEquals(2.days, policy.retentionFor(JobBucket.SUCCEEDED))
    assertEquals(14.days, policy.retentionFor(JobBucket.DELETED))
  }

  @Test
  fun `retention policy rejects non positive global retention`() {
    assertFailsWith<IllegalArgumentException> {
      RetentionPolicy(global = 0.seconds)
    }
  }
}
