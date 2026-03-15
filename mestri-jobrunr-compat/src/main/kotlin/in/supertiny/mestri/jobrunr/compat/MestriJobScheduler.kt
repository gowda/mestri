package `in`.supertiny.mestri.jobrunr.compat

import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy
import `in`.supertiny.mestri.core.serialization.JobPayload
import `in`.supertiny.mestri.redis.RedisImmediateJobEnqueuer
import `in`.supertiny.mestri.redis.RedisScheduledJobStore
import org.jobrunr.jobs.JobId
import org.jobrunr.jobs.lambdas.JobLambda
import org.jobrunr.scheduling.JobScheduler
import java.time.Instant
import `in`.supertiny.mestri.core.model.JobId as MestriJobId

class MestriJobScheduler(
  private val immediateJobEnqueuer: RedisImmediateJobEnqueuer,
  private val scheduledJobStore: RedisScheduledJobStore,
  private val defaultQueue: QueueName = QueueName("default"),
  private val defaultRetryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
  private val jobIdGenerator: () -> MestriJobId = { MestriJobId.random() },
) : JobScheduler {
  override fun enqueue(job: JobLambda): JobId {
    val payload = payloadFor(job)

    return JobId.from(immediateJobEnqueuer.enqueue(payload).jobId)
  }

  override fun schedule(
    instant: Instant,
    job: JobLambda,
  ): JobId {
    val payload = payloadFor(job)

    return JobId.from(scheduledJobStore.persistScheduled(payload, dueAt = instant).jobId)
  }

  private fun payloadFor(job: JobLambda): JobPayload {
    val invocation = job.toInvocationDescriptor()

    return JobPayload(
      jobId = jobIdGenerator(),
      invocation = invocation,
      queue = defaultQueue,
      retryPolicy = defaultRetryPolicy,
      labels = invocation.labels,
    )
  }
}
