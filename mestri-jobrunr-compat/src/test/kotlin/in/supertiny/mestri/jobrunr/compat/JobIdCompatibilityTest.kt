package `in`.supertiny.mestri.jobrunr.compat

import org.jobrunr.jobs.JobId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import `in`.supertiny.mestri.core.model.JobId as MestriJobId

class JobIdCompatibilityTest {
  @Test
  fun `job id converts to and from mestri job id`() {
    val mestriJobId = MestriJobId("job-compat-123")
    val jobId = JobId.from(mestriJobId)

    assertEquals("job-compat-123", jobId.value)
    assertEquals(mestriJobId, jobId.toMestriJobId())
  }

  @Test
  fun `job id string form is the raw value`() {
    assertEquals("job-compat-123", JobId("job-compat-123").toString())
  }

  @Test
  fun `job id factory creates compat job id`() {
    assertEquals("job-compat-456", JobId.of("job-compat-456").value)
  }

  @Test
  fun `job id rejects blank values`() {
    assertFailsWith<IllegalArgumentException> {
      JobId(" ")
    }
  }
}
