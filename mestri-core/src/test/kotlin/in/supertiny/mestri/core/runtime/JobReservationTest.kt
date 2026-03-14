package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class JobReservationTest {
  @Test
  fun `job reservation rejects mismatched reserved time`() {
    assertFailsWith<IllegalArgumentException> {
      JobReservation(
        lease =
          JobLease(
            jobId = JobId("job-1"),
            workerId = WorkerId("worker-1"),
            acquiredAt = Instant.parse("2026-03-15T10:00:00Z"),
            expiresAt = Instant.parse("2026-03-15T10:05:00Z"),
          ),
        payload = samplePayload(),
        reservedAt = Instant.parse("2026-03-15T10:00:01Z"),
      )
    }
  }

  @Test
  fun `job reservation rejects mismatched payload job id`() {
    assertFailsWith<IllegalArgumentException> {
      JobReservation(
        lease =
          JobLease(
            jobId = JobId("job-1"),
            workerId = WorkerId("worker-1"),
            acquiredAt = Instant.parse("2026-03-15T10:00:00Z"),
            expiresAt = Instant.parse("2026-03-15T10:05:00Z"),
          ),
        payload = samplePayload(jobId = "job-2"),
        reservedAt = Instant.parse("2026-03-15T10:00:00Z"),
      )
    }
  }

  private fun samplePayload(jobId: String = "job-1"): JobPayload =
    JobPayload(
      jobId = JobId(jobId),
      invocation =
        InvocationDescriptor(
          target =
            SpringBeanTarget(
              className = "io.example.jobs.InvoiceJobs",
              beanName = "invoiceJobs",
            ),
          method =
            InvocationMethod(
              name = "generateInvoice",
              parameterTypeNames = listOf("java.lang.String"),
            ),
          arguments = listOf(InvocationArgument("java.lang.String", "\"user-1\"")),
        ),
      queue = QueueName("billing"),
    )
}
