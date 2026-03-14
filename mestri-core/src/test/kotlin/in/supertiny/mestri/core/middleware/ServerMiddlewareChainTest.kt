package `in`.supertiny.mestri.core.middleware

import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServerMiddlewareChainTest {

  @Test
  fun `server middleware chain applies middleware in order`() {
    val callOrder = mutableListOf<String>()
    val chain =
      ServerMiddlewareChain(
        middlewares =
          listOf(
            ServerMiddleware { request, next ->
              callOrder += "first-before"
              val result =
                next.proceed(
                  request.copy(metadata = request.metadata + ("traceId" to "trace-1"))
                )
              callOrder += "first-after"
              result
            },
            ServerMiddleware { request, next ->
              callOrder += "second-before"
              val result = next.proceed(request.copy(attempt = request.attempt + 1))
              callOrder += "second-after"
              result
            },
          ),
        terminal =
          ServerChain { request ->
            callOrder += "terminal"
            ServerExecutionResult(
              outcome = ServerExecutionOutcome.SUCCEEDED,
              metadata = request.metadata + ("attempt" to request.attempt.toString()),
            )
          },
      )

    val result = chain.proceed(sampleRequest())

    assertEquals(
      listOf("first-before", "second-before", "terminal", "second-after", "first-after"),
      callOrder,
    )
    assertEquals(ServerExecutionOutcome.SUCCEEDED, result.outcome)
    assertEquals("trace-1", result.metadata["traceId"])
    assertEquals("2", result.metadata["attempt"])
  }

  @Test
  fun `server middleware chain can short circuit terminal execution`() {
    val chain =
      ServerMiddlewareChain(
        middlewares =
          listOf(
            ServerMiddleware { _, _ ->
              ServerExecutionResult(
                outcome = ServerExecutionOutcome.TERMINAL_FAILURE,
                errorMessage = "blocked by middleware",
              )
            }
          ),
        terminal =
          ServerChain {
            error("terminal should not be called")
          },
      )

    val result = chain.proceed(sampleRequest())

    assertEquals(ServerExecutionOutcome.TERMINAL_FAILURE, result.outcome)
    assertEquals("blocked by middleware", result.errorMessage)
  }

  @Test
  fun `server execution request rejects attempt smaller than one`() {
    assertFailsWith<IllegalArgumentException> {
      sampleRequest().copy(attempt = 0)
    }
  }

  private fun sampleRequest(): ServerExecutionRequest =
    ServerExecutionRequest(
      jobId = JobId("job-123"),
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
      attempt = 1,
    )
}
