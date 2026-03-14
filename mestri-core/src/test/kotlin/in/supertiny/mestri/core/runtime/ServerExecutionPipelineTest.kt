package `in`.supertiny.mestri.core.runtime

import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.middleware.ServerMiddleware
import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.serialization.JobPayload
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServerExecutionPipelineTest {

  @Test
  fun `server execution pipeline invokes middleware and handler in order`() {
    val callOrder = mutableListOf<String>()
    val pipeline =
      ServerExecutionPipeline(
        middlewares =
          listOf(
            ServerMiddleware { request, next ->
              callOrder += "middleware-before"
              val result =
                next.proceed(
                  request.copy(metadata = request.metadata + ("traceId" to "trace-1"))
                )
              callOrder += "middleware-after"
              result
            }
          ),
        handlerInvoker =
          JobHandlerInvoker { reservation, request ->
            callOrder += "handler"
            assertEquals(reservation.payload.jobId, request.jobId)
            assertEquals("trace-1", request.metadata["traceId"])
          },
      )

    val result = pipeline.execute(sampleReservation())

    assertEquals(
      listOf("middleware-before", "handler", "middleware-after"),
      callOrder,
    )
    assertEquals(ServerExecutionOutcome.SUCCEEDED, result.outcome)
    assertEquals("trace-1", result.metadata["traceId"])
  }

  @Test
  fun `server execution pipeline maps handler exception to failure result`() {
    val pipeline =
      ServerExecutionPipeline(
        middlewares = emptyList(),
        handlerInvoker =
          JobHandlerInvoker { _, _ ->
            error("handler failed")
          },
      )

    val result = pipeline.execute(sampleReservation())

    assertEquals(ServerExecutionOutcome.TERMINAL_FAILURE, result.outcome)
    assertEquals("handler failed", result.errorMessage)
  }

  private fun sampleReservation(): JobReservation =
    JobReservation(
      lease =
        JobLease(
          jobId = JobId("job-123"),
          workerId = WorkerId("worker-1"),
          acquiredAt = Instant.parse("2026-03-15T12:01:00Z"),
          expiresAt = Instant.parse("2026-03-15T12:06:00Z"),
        ),
      payload =
        JobPayload(
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
          queue = QueueName("billing"),
        ),
      reservedAt = Instant.parse("2026-03-15T12:01:00Z"),
    )
}
