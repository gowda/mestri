package `in`.supertiny.mestri.core.middleware

import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName

data class ClientEnqueueResult(
  val jobId: JobId,
  val queue: QueueName,
  val metadata: Map<String, String> = emptyMap(),
)
