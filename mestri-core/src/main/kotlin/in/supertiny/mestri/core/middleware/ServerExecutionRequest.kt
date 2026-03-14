package `in`.supertiny.mestri.core.middleware

import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName

data class ServerExecutionRequest(
  val jobId: JobId,
  val invocation: InvocationDescriptor,
  val queue: QueueName,
  val attempt: Int,
  val metadata: Map<String, String> = emptyMap(),
) {
  init {
    require(attempt >= 1) { "Server execution attempt must be at least 1." }
    require(metadata.keys.none { it.isBlank() }) { "Server metadata keys must not be blank." }
    require(metadata.values.none { it.isBlank() }) { "Server metadata values must not be blank." }
  }
}
