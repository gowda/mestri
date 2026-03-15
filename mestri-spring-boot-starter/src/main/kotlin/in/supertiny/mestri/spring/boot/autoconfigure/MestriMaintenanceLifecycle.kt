package `in`.supertiny.mestri.spring.boot.autoconfigure

import `in`.supertiny.mestri.core.runtime.MaintenanceLoop
import org.springframework.context.SmartLifecycle
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MestriMaintenanceLifecycle(
  private val maintenanceLoop: MaintenanceLoop,
  private val interval: Duration,
) : SmartLifecycle {
  private val running = AtomicBoolean(false)

  @Volatile
  private var executor: ScheduledExecutorService? = null

  override fun start() {
    if (!running.compareAndSet(false, true)) return

    val lifecycleExecutor = Executors.newSingleThreadScheduledExecutor()
    executor = lifecycleExecutor
    lifecycleExecutor.scheduleWithFixedDelay(
      {
        maintenanceLoop.runCycle()
      },
      0L,
      interval.toMillis(),
      TimeUnit.MILLISECONDS,
    )
  }

  override fun stop() {
    if (!running.compareAndSet(true, false)) return

    executor?.shutdown()
    executor?.awaitTermination(5, TimeUnit.SECONDS)
    executor = null
  }

  override fun stop(callback: Runnable) {
    try {
      stop()
    } finally {
      callback.run()
    }
  }

  override fun isRunning(): Boolean = running.get()

  override fun isAutoStartup(): Boolean = true

  override fun getPhase(): Int = 20
}
