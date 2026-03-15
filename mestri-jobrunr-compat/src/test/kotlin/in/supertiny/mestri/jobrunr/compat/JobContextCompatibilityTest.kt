package `in`.supertiny.mestri.jobrunr.compat

import org.jobrunr.jobs.JobId
import org.jobrunr.jobs.context.MestriJobContext
import org.jobrunr.jobs.context.NoOpJobContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JobContextCompatibilityTest {
  @Test
  fun `no op job context exposes null safe defaults`() {
    assertNull(NoOpJobContext.jobId)
    assertNull(NoOpJobContext.queue)
    assertNull(NoOpJobContext.attempt)
    assertEquals(emptyMap(), NoOpJobContext.metadata)
  }

  @Test
  fun `mestri job context captures runtime fields`() {
    val context =
      MestriJobContext(
        jobId = JobId("job-ctx-1"),
        queue = "billing",
        attempt = 2,
        metadata = mapOf("traceId" to "trace-1"),
      )

    assertEquals("job-ctx-1", context.jobId.value)
    assertEquals("billing", context.queue)
    assertEquals(2, context.attempt)
    assertEquals("trace-1", context.metadata["traceId"])
  }
}
