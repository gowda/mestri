package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeadLetteringTest {
  @Test
  fun `dead lettering defaults to dead bucket`() {
    val deadLettering =
      DeadLettering(
        jobId = JobId("job-123"),
        recordedAt = Instant.parse("2026-03-15T12:10:00Z"),
      )

    assertEquals(JobBucket.DEAD, deadLettering.bucket)
  }

  @Test
  fun `dead lettering rejects non dead bucket`() {
    assertFailsWith<IllegalArgumentException> {
      DeadLettering(
        jobId = JobId("job-123"),
        bucket = JobBucket.RETRY,
        recordedAt = Instant.parse("2026-03-15T12:10:00Z"),
      )
    }
  }
}
