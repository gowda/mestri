package org.jobrunr.scheduling

import org.jobrunr.jobs.JobId
import org.jobrunr.jobs.lambdas.JobLambda

fun interface JobScheduler {
  fun enqueue(job: JobLambda): JobId
}
