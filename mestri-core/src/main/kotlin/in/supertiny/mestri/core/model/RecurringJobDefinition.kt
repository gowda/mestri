package `in`.supertiny.mestri.core.model

import java.time.Instant

data class RecurringJobDefinition(
  val id: RecurringJobId,
  val invocation: InvocationDescriptor,
  val queue: QueueName,
  val schedule: RecurringSchedule,
  val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
  val labels: Set<String> = emptySet(),
  val metadata: Map<String, String> = emptyMap(),
  val deduplicationKey: DeduplicationKey? = null,
  val deduplicationPolicy: DeduplicationPolicy? = null,
  val enabled: Boolean = true,
  val createdAt: Instant,
  val updatedAt: Instant,
  val nextRunAt: Instant? = null,
  val lastEnqueuedAt: Instant? = null,
) {
  init {
    require(labels.none { it.isBlank() }) {
      "Recurring job definition labels must not be blank."
    }
    require(metadata.keys.none { it.isBlank() }) {
      "Recurring job definition metadata keys must not be blank."
    }
    require(metadata.values.none { it.isBlank() }) {
      "Recurring job definition metadata values must not be blank."
    }
    require(deduplicationPolicy == null || deduplicationKey != null) {
      "Recurring job definition deduplicationPolicy requires a deduplicationKey."
    }
    require(!updatedAt.isBefore(createdAt)) {
      "Recurring job definition updatedAt must not be before createdAt."
    }
  }
}
