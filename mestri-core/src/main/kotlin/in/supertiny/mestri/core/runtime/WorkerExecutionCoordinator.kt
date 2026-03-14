package `in`.supertiny.mestri.core.runtime

enum class WorkerLifecycleState {
  RUNNING,
  SHUTTING_DOWN,
  STOPPED,
}

enum class WorkerCycleOutcome {
  IDLE,
  PROCESSED,
  STOPPED,
}

data class WorkerCycleResult(
  val outcome: WorkerCycleOutcome,
  val state: WorkerLifecycleState,
)

fun interface JobReservationSource {
  fun reserve(): JobReservation?
}

fun interface JobReservationProcessor {
  fun process(reservation: JobReservation)
}

class WorkerExecutionCoordinator(
  private val reservationSource: JobReservationSource,
  private val reservationProcessor: JobReservationProcessor,
) {
  var lifecycleState: WorkerLifecycleState = WorkerLifecycleState.RUNNING
    private set

  var inFlightReservations: Int = 0
    private set

  private var shutdownRequested: Boolean = false

  fun requestShutdown() {
    shutdownRequested = true
    lifecycleState =
      if (inFlightReservations > 0) {
        WorkerLifecycleState.SHUTTING_DOWN
      } else {
        WorkerLifecycleState.STOPPED
      }
  }

  fun runNext(): WorkerCycleResult {
    if (lifecycleState == WorkerLifecycleState.STOPPED ||
      (shutdownRequested && inFlightReservations == 0)
    ) {
      lifecycleState = WorkerLifecycleState.STOPPED
      return WorkerCycleResult(
        outcome = WorkerCycleOutcome.STOPPED,
        state = lifecycleState,
      )
    }

    val reservation = reservationSource.reserve()
    if (reservation == null) {
      if (shutdownRequested) {
        lifecycleState = WorkerLifecycleState.STOPPED
        return WorkerCycleResult(
          outcome = WorkerCycleOutcome.STOPPED,
          state = lifecycleState,
        )
      }

      lifecycleState = WorkerLifecycleState.RUNNING
      return WorkerCycleResult(
        outcome = WorkerCycleOutcome.IDLE,
        state = lifecycleState,
      )
    }

    inFlightReservations += 1
    lifecycleState =
      if (shutdownRequested) {
        WorkerLifecycleState.SHUTTING_DOWN
      } else {
        WorkerLifecycleState.RUNNING
      }

    try {
      reservationProcessor.process(reservation)
    } finally {
      inFlightReservations -= 1
      lifecycleState =
        if (shutdownRequested) {
          WorkerLifecycleState.STOPPED
        } else {
          WorkerLifecycleState.RUNNING
        }
    }

    return WorkerCycleResult(
      outcome = WorkerCycleOutcome.PROCESSED,
      state = lifecycleState,
    )
  }
}
