package `in`.supertiny.mestri.jobrunr.compat

import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy
import `in`.supertiny.mestri.core.serialization.JobPayload
import `in`.supertiny.mestri.redis.RedisImmediateJobEnqueuer
import `in`.supertiny.mestri.redis.RedisScheduledJobStore
import org.jobrunr.jobs.JobId
import org.jobrunr.jobs.annotations.Job
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
    val annotation = resolveJobAnnotation(invocation)
    val metadata =
      buildMap {
        annotation
          ?.name
          ?.takeIf { name -> name.isNotBlank() }
          ?.let { name -> put(METADATA_JOB_NAME, name) }
      }

    return JobPayload(
      jobId = jobIdGenerator(),
      invocation = invocation,
      queue = defaultQueue,
      retryPolicy = retryPolicyFor(annotation),
      labels = invocation.labels,
      metadata = metadata,
    )
  }

  private fun retryPolicyFor(annotation: Job?): RetryPolicy {
    val retries = annotation?.retries ?: Job.DEFAULT_RETRIES_UNSET
    if (retries == Job.DEFAULT_RETRIES_UNSET) {
      return defaultRetryPolicy
    }

    require(retries >= 0) { "@Job retries must be zero or greater." }

    return defaultRetryPolicy.copy(maxAttempts = retries + 1)
  }

  private fun resolveJobAnnotation(
    invocation: `in`.supertiny.mestri.core.model.InvocationDescriptor,
  ): Job? {
    val parameterTypes =
      invocation.method.parameterTypeNames
        .map(::resolveClass)
        .toTypedArray()

    val method =
      runCatching {
        resolveClass(invocation.target.className).getMethod(invocation.method.name, *parameterTypes)
      }.getOrNull()

    return method?.getAnnotation(Job::class.java)
  }

  private fun resolveClass(typeName: String): Class<*> =
    primitiveTypes[typeName] ?: Class.forName(typeName)

  companion object {
    const val METADATA_JOB_NAME: String = "jobrunr.jobName"

    private val primitiveTypes =
      mapOf(
        "boolean" to Boolean::class.javaPrimitiveType!!,
        "byte" to Byte::class.javaPrimitiveType!!,
        "char" to Char::class.javaPrimitiveType!!,
        "double" to Double::class.javaPrimitiveType!!,
        "float" to Float::class.javaPrimitiveType!!,
        "int" to Int::class.javaPrimitiveType!!,
        "long" to Long::class.javaPrimitiveType!!,
        "short" to Short::class.javaPrimitiveType!!,
      )
  }
}
