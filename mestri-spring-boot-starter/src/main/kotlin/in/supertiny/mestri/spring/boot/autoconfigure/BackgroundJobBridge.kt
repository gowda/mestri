package `in`.supertiny.mestri.spring.boot.autoconfigure

import org.jobrunr.scheduling.BackgroundJob
import org.jobrunr.scheduling.JobScheduler

class BackgroundJobBridge(
  jobScheduler: JobScheduler,
) : AutoCloseable {
  init {
    BackgroundJob.configure(jobScheduler)
  }

  override fun close() {
    BackgroundJob.reset()
  }
}
