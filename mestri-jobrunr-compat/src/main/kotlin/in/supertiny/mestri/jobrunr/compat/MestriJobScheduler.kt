package `in`.supertiny.mestri.jobrunr.compat

import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy
import `in`.supertiny.mestri.core.serialization.JobPayload
import `in`.supertiny.mestri.redis.RedisImmediateJobEnqueuer
import org.jobrunr.jobs.JobId
import org.jobrunr.jobs.lambdas.JobLambda
import org.jobrunr.scheduling.JobScheduler
import `in`.supertiny.mestri.core.model.JobId as MestriJobId

class MestriJobScheduler(
  private val immediateJobEnqueuer: RedisImmediateJobEnqueuer,
  private val defaultQueue: QueueName = QueueName("default"),
  private val defaultRetryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
  private val jobIdGenerator: () -> MestriJobId = { MestriJobId.random() },
) : JobScheduler {
  override fun enqueue(job: JobLambda): JobId {
    val invocation = job.toInvocationDescriptor()
    val payload =
      JobPayload(
        jobId = jobIdGenerator(),
        invocation = invocation,
        queue = defaultQueue,
        retryPolicy = defaultRetryPolicy,
        labels = invocation.labels,
      )

    return JobId.from(immediateJobEnqueuer.enqueue(payload).jobId)
  }
}
