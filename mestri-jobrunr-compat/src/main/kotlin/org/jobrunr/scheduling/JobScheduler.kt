package org.jobrunr.scheduling

import org.jobrunr.jobs.JobId
import org.jobrunr.jobs.lambdas.JobLambda
import java.time.Instant

interface JobScheduler {
  fun enqueue(job: JobLambda): JobId

  fun schedule(
    instant: Instant,
    job: JobLambda,
  ): JobId
}
