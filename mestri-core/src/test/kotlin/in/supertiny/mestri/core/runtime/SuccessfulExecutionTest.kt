package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SuccessfulExecutionTest {

  @Test
  fun `successful execution defaults to succeeded bucket`() {
    val execution =
      SuccessfulExecution(
        jobId = JobId("job-123"),
        completedAt = Instant.parse("2026-03-15T12:10:00Z"),
      )

    assertEquals(JobBucket.SUCCEEDED, execution.bucket)
  }

  @Test
  fun `successful execution rejects non succeeded buckets`() {
    assertFailsWith<IllegalArgumentException> {
      SuccessfulExecution(
        jobId = JobId("job-123"),
        bucket = JobBucket.DEAD,
        completedAt = Instant.parse("2026-03-15T12:10:00Z"),
      )
    }
  }
}
