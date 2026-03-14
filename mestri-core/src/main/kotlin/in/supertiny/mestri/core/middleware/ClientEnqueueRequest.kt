package `in`.supertiny.mestri.core.middleware

import `in`.supertiny.mestri.core.model.DeduplicationKey
import `in`.supertiny.mestri.core.model.DeduplicationPolicy
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.RetryPolicy

data class ClientEnqueueRequest(
  val invocation: InvocationDescriptor,
  val queue: QueueName = QueueName.DEFAULT,
  val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
  val metadata: Map<String, String> = emptyMap(),
  val deduplicationKey: DeduplicationKey? = null,
  val deduplicationPolicy: DeduplicationPolicy? = null,
) {
  init {
    require(metadata.keys.none { it.isBlank() }) { "Client metadata keys must not be blank." }
    require(metadata.values.none { it.isBlank() }) { "Client metadata values must not be blank." }
    require(deduplicationPolicy == null || deduplicationKey != null) {
      "Client deduplicationPolicy requires a deduplicationKey."
    }
  }
}
