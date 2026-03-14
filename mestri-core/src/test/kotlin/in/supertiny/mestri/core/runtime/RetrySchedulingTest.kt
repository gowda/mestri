package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetrySchedulingTest {

  @Test
  fun `retry scheduling defaults to retry bucket`() {
    val scheduling =
      RetryScheduling(
        jobId = JobId("job-123"),
        attempt = 2,
        dueAt = Instant.parse("2026-03-15T12:10:00Z"),
      )

    assertEquals(JobBucket.RETRY, scheduling.bucket)
  }

  @Test
  fun `retry scheduling rejects non retry bucket`() {
    assertFailsWith<IllegalArgumentException> {
      RetryScheduling(
        jobId = JobId("job-123"),
        bucket = JobBucket.DEAD,
        attempt = 2,
        dueAt = Instant.parse("2026-03-15T12:10:00Z"),
      )
    }
  }

  @Test
  fun `retry scheduling rejects non positive attempt`() {
    assertFailsWith<IllegalArgumentException> {
      RetryScheduling(
        jobId = JobId("job-123"),
        attempt = 0,
        dueAt = Instant.parse("2026-03-15T12:10:00Z"),
      )
    }
  }
}
