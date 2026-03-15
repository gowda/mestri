package org.jobrunr.scheduling

import org.jobrunr.jobs.JobId
import org.jobrunr.jobs.lambdas.JobLambda
import java.util.concurrent.atomic.AtomicReference

object BackgroundJob {
  private val jobSchedulerRef = AtomicReference<JobScheduler?>()

  fun enqueue(job: JobLambda): JobId = requireJobScheduler().enqueue(job)

  fun configure(jobScheduler: JobScheduler) {
    jobSchedulerRef.set(jobScheduler)
  }

  fun reset() {
    jobSchedulerRef.set(null)
  }

  private fun requireJobScheduler(): JobScheduler =
    jobSchedulerRef.get()
      ?: error("BackgroundJob requires a configured JobScheduler before use.")
}
