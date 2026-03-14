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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkerExecutionCoordinatorTest {
  @Test
  fun `run next processes reservation while worker is running`() {
    val reservation = sampleReservation()
    var processedJobId: JobId? = null
    val coordinator =
      WorkerExecutionCoordinator(
        reservationSource = JobReservationSource { reservation },
        reservationProcessor = JobReservationProcessor { processedJobId = it.payload.jobId },
      )

    val result = coordinator.runNext()

    assertEquals(WorkerCycleOutcome.PROCESSED, result.outcome)
    assertEquals(WorkerLifecycleState.RUNNING, result.state)
    assertEquals(JobId("job-123"), processedJobId)
    assertEquals(0, coordinator.inFlightReservations)
  }

  @Test
  fun `request shutdown before polling stops worker without reserving new work`() {
    var reserveCalls = 0
    val coordinator =
      WorkerExecutionCoordinator(
        reservationSource =
          JobReservationSource {
            reserveCalls += 1
            sampleReservation()
          },
        reservationProcessor = JobReservationProcessor { },
      )

    coordinator.requestShutdown()
    val result = coordinator.runNext()

    assertEquals(WorkerCycleOutcome.STOPPED, result.outcome)
    assertEquals(WorkerLifecycleState.STOPPED, result.state)
    assertEquals(0, reserveCalls)
  }

  @Test
  fun `request shutdown during processing lets in flight reservation finish before stopping`() {
    val reservation = sampleReservation()
    lateinit var coordinator: WorkerExecutionCoordinator
    var sawShuttingDown = false
    coordinator =
      WorkerExecutionCoordinator(
        reservationSource = JobReservationSource { reservation },
        reservationProcessor =
          JobReservationProcessor {
            coordinator.requestShutdown()
            sawShuttingDown = coordinator.lifecycleState == WorkerLifecycleState.SHUTTING_DOWN
            assertEquals(1, coordinator.inFlightReservations)
          },
      )

    val firstResult = coordinator.runNext()
    val secondResult = coordinator.runNext()

    assertTrue(sawShuttingDown)
    assertEquals(WorkerCycleOutcome.PROCESSED, firstResult.outcome)
    assertEquals(WorkerLifecycleState.STOPPED, firstResult.state)
    assertEquals(0, coordinator.inFlightReservations)
    assertEquals(WorkerCycleOutcome.STOPPED, secondResult.outcome)
    assertEquals(WorkerLifecycleState.STOPPED, secondResult.state)
  }

  @Test
  fun `run next returns idle when no reservation is available`() {
    val coordinator =
      WorkerExecutionCoordinator(
        reservationSource = JobReservationSource { null },
        reservationProcessor = JobReservationProcessor { },
      )

    val result = coordinator.runNext()

    assertEquals(WorkerCycleOutcome.IDLE, result.outcome)
    assertEquals(WorkerLifecycleState.RUNNING, result.state)
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
