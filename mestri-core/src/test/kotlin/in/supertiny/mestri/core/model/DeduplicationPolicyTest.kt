package `in`.supertiny.mestri.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes

class DeduplicationPolicyTest {
  @Test
  fun `deduplication policy captures scope strategy and ttl`() {
    val policy =
      DeduplicationPolicy(
        scope = DeduplicationScope.SCHEDULE,
        strategy = DeduplicationStrategy.COALESCE,
        ttl = 30.minutes,
      )

    assertEquals(DeduplicationScope.SCHEDULE, policy.scope)
    assertEquals(DeduplicationStrategy.COALESCE, policy.strategy)
    assertEquals(30.minutes, policy.ttl)
  }

  @Test
  fun `deduplication key rejects blank value`() {
    assertFailsWith<IllegalArgumentException> {
      DeduplicationKey("")
    }
  }
}
