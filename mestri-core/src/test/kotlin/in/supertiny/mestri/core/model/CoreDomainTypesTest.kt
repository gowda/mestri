package `in`.supertiny.mestri.core.model

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
  fun `default queue name is stable`() {
    assertEquals("default", QueueName.DEFAULT.value)
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
    assertEquals(14.days, policy.retentionFor(JobBucket.QUEUED))
  }

  @Test
  fun `retention policy rejects non positive global retention`() {
    assertFailsWith<IllegalArgumentException> {
      RetentionPolicy(global = 0.seconds)
    }
  }

  @Test
  fun `spring invocation descriptor stores target method and arguments`() {
    val descriptor =
      InvocationDescriptor(
        target =
          SpringBeanTarget(
            className = "io.example.jobs.InvoiceJobs",
            beanName = "invoiceJobs",
          ),
        method =
          InvocationMethod(
            name = "generateInvoice",
            parameterTypeNames = listOf("java.lang.String", "java.lang.Long"),
          ),
        arguments =
          listOf(
            InvocationArgument("java.lang.String", "\"user-1\""),
            InvocationArgument("java.lang.Long", "42"),
          ),
        labels = setOf("billing", "invoice"),
      )

    assertEquals("invoiceJobs", (descriptor.target as SpringBeanTarget).beanName)
    assertEquals("generateInvoice", descriptor.method.name)
    assertEquals(2, descriptor.arguments.size)
  }

  @Test
  fun `standalone invocation descriptor supports handler ids`() {
    val descriptor =
      InvocationDescriptor(
        target =
          StandaloneHandlerTarget(
            className = "io.example.jobs.StandaloneInvoiceHandler",
            handlerId = "invoice-handler",
          ),
        method = InvocationMethod(name = "handle"),
      )

    assertEquals("invoice-handler", (descriptor.target as StandaloneHandlerTarget).handlerId)
  }

  @Test
  fun `invocation descriptor rejects mismatched parameter types`() {
    assertFailsWith<IllegalArgumentException> {
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
        arguments =
          listOf(
            InvocationArgument("java.lang.Long", "42"),
          ),
      )
    }
  }
}
