package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.DeduplicationKey
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.runtime.ProcessId
import `in`.supertiny.mestri.core.runtime.WorkerId

object RedisKeyspace {
  const val ROOT: String = "mestri"

  fun queue(queueName: QueueName): String = "$ROOT:queue:${queueName.value}"

  fun scheduled(): String = "$ROOT:scheduled"

  fun retry(): String = "$ROOT:retry"

  fun dead(): String = "$ROOT:dead"

  fun succeeded(): String = "$ROOT:succeeded"

  fun deleted(): String = "$ROOT:deleted"

  fun job(jobId: JobId): String = "$ROOT:job:${jobId.value}"

  fun processRegistry(): String = "$ROOT:processes"

  fun workerRegistry(): String = "$ROOT:workers"

  fun leaseRegistry(): String = "$ROOT:leases"

  fun process(processId: ProcessId): String = "$ROOT:process:${processId.value}"

  fun processWork(processId: ProcessId): String = "$ROOT:process:${processId.value}:work"

  fun workerLease(jobId: JobId): String = "$ROOT:lease:${jobId.value}"

  fun workerHeartbeat(workerId: WorkerId): String = "$ROOT:worker:${workerId.value}:heartbeat"

  fun deduplication(key: DeduplicationKey): String = "$ROOT:dedup:${key.value}"

  fun recurringDefinitions(): String = "$ROOT:recurring"

  fun recurringDefinition(definitionId: String): String {
    require(definitionId.isNotBlank()) { "Recurring definition id must not be blank." }
    return "$ROOT:recurring:$definitionId"
  }
}
