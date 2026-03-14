package `in`.supertiny.mestri.core.serialization

import `in`.supertiny.mestri.core.model.DeduplicationKey
import `in`.supertiny.mestri.core.model.DeduplicationPolicy
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy

data class JobPayload(
  val jobId: JobId,
  val invocation: InvocationDescriptor,
  val queue: QueueName,
  val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
  val labels: Set<String> = emptySet(),
  val metadata: Map<String, String> = emptyMap(),
  val deduplicationKey: DeduplicationKey? = null,
  val deduplicationPolicy: DeduplicationPolicy? = null,
) {
  init {
    require(labels.none { it.isBlank() }) { "Job payload labels must not be blank." }
    require(metadata.keys.none { it.isBlank() }) { "Job payload metadata keys must not be blank." }
    require(
      metadata.values.none { it.isBlank() },
    ) { "Job payload metadata values must not be blank." }
    require(deduplicationPolicy == null || deduplicationKey != null) {
      "Job payload deduplicationPolicy requires a deduplicationKey."
    }
  }
}
