package `in`.supertiny.mestri.core.middleware

import `in`.supertiny.mestri.core.model.DeduplicationKey
import `in`.supertiny.mestri.core.model.DeduplicationPolicy
import `in`.supertiny.mestri.core.model.DeduplicationScope
import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClientMiddlewareChainTest {
  @Test
  fun `client middleware chain applies middleware in order`() {
    val callOrder = mutableListOf<String>()
    val chain =
      ClientMiddlewareChain(
        middlewares =
          listOf(
            ClientMiddleware { request, next ->
              callOrder += "first-before"
              val result =
                next.proceed(
                  request.copy(metadata = request.metadata + ("traceId" to "trace-1")),
                )
              callOrder += "first-after"
              result
            },
            ClientMiddleware { request, next ->
              callOrder += "second-before"
              val result = next.proceed(request.copy(queue = QueueName("critical")))
              callOrder += "second-after"
              result
            },
          ),
        terminal =
          ClientChain { request ->
            callOrder += "terminal"
            ClientEnqueueResult(
              jobId = JobId("job-123"),
              queue = request.queue,
              metadata = request.metadata,
            )
          },
      )

    val result = chain.proceed(sampleRequest())

    assertEquals(
      listOf("first-before", "second-before", "terminal", "second-after", "first-after"),
      callOrder,
    )
    assertEquals(QueueName("critical"), result.queue)
    assertEquals("trace-1", result.metadata["traceId"])
  }

  @Test
  fun `client middleware chain can short circuit terminal execution`() {
    val chain =
      ClientMiddlewareChain(
        middlewares =
          listOf(
            ClientMiddleware { _, _ ->
              ClientEnqueueResult(
                jobId = JobId("short-circuit"),
                queue = QueueName("bulk"),
              )
            },
          ),
        terminal =
          ClientChain {
            error("terminal should not be called")
          },
      )

    val result = chain.proceed(sampleRequest())

    assertEquals(JobId("short-circuit"), result.jobId)
    assertEquals(QueueName("bulk"), result.queue)
  }

  @Test
  fun `client middleware chain without middleware delegates directly to terminal`() {
    val chain =
      ClientMiddlewareChain(
        middlewares = emptyList(),
        terminal =
          ClientChain { request ->
            ClientEnqueueResult(
              jobId = JobId("terminal-only"),
              queue = request.queue,
              metadata = request.metadata,
            )
          },
      )

    val result = chain.proceed(sampleRequest().copy(metadata = mapOf("traceId" to "trace-1")))

    assertEquals(JobId("terminal-only"), result.jobId)
    assertEquals("trace-1", result.metadata["traceId"])
  }

  @Test
  fun `client enqueue request rejects blank metadata`() {
    assertFailsWith<IllegalArgumentException> {
      sampleRequest().copy(metadata = mapOf("traceId" to ""))
    }
  }

  @Test
  fun `client enqueue request rejects blank metadata key`() {
    assertFailsWith<IllegalArgumentException> {
      sampleRequest().copy(metadata = mapOf("" to "trace-1"))
    }
  }

  @Test
  fun `client enqueue request rejects blank deduplication key`() {
    assertFailsWith<IllegalArgumentException> {
      sampleRequest().copy(deduplicationKey = DeduplicationKey(""))
    }
  }

  @Test
  fun `client enqueue request requires key when deduplication policy is set`() {
    assertFailsWith<IllegalArgumentException> {
      sampleRequest().copy(
        deduplicationPolicy = DeduplicationPolicy(scope = DeduplicationScope.ENQUEUE),
      )
    }
  }

  private fun sampleRequest(): ClientEnqueueRequest =
    ClientEnqueueRequest(
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
    )
}
