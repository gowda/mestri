package `in`.supertiny.mestri.core.serialization

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JobPayloadContractsTest {

  @Test
  fun `job payload captures default transport shape`() {
    val payload =
      JobPayload(
        jobId = JobId("job-1"),
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
            labels = setOf("billing"),
          ),
        queue = QueueName("critical"),
        labels = setOf("high-priority"),
        metadata = mapOf("traceId" to "trace-1"),
        deduplicationKey = "invoice-user-1",
      )

    assertEquals(JobId("job-1"), payload.jobId)
    assertEquals(QueueName("critical"), payload.queue)
    assertEquals("trace-1", payload.metadata["traceId"])
  }

  @Test
  fun `serialized payload rejects blank content`() {
    assertFailsWith<IllegalArgumentException> {
      SerializedPayload(format = PayloadFormat.JSON, content = "")
    }
  }

  @Test
  fun `job payload rejects blank metadata value`() {
    assertFailsWith<IllegalArgumentException> {
      samplePayload().copy(metadata = mapOf("traceId" to ""))
    }
  }

  private fun samplePayload(): JobPayload =
    JobPayload(
      jobId = JobId("job-1"),
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
      queue = QueueName.DEFAULT,
    )
}
