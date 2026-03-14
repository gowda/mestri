package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.runtime.ProcessId
import `in`.supertiny.mestri.core.runtime.WorkerHeartbeat
import `in`.supertiny.mestri.core.runtime.WorkerId
import java.time.Instant

class RedisWorkerHeartbeatStore(
  private val hashAccessor: RedisHashAccessor,
  private val setAccessor: RedisSetAccessor,
) {
  fun save(heartbeat: WorkerHeartbeat) {
    hashAccessor.putAll(
      RedisKeyspace.workerHeartbeat(heartbeat.workerId),
      mapOf(
        FIELD_WORKER_ID to heartbeat.workerId.value,
        FIELD_PROCESS_ID to heartbeat.processId.value,
        FIELD_QUEUES to heartbeat.queues.joinToString(","),
        FIELD_RECORDED_AT to heartbeat.recordedAt.toString(),
        FIELD_EXPIRES_AT to heartbeat.expiresAt.toString(),
      ),
    )
    setAccessor.add(RedisKeyspace.processRegistry(), heartbeat.processId.value)
  }

  fun find(workerId: WorkerId): WorkerHeartbeat? {
    val fields = hashAccessor.entries(RedisKeyspace.workerHeartbeat(workerId))
    if (fields.isEmpty()) return null

    val queues =
      requireField(fields, FIELD_QUEUES)
        .split(',')
        .filter { it.isNotBlank() }
        .toSet()

    return WorkerHeartbeat(
      workerId = WorkerId(requireField(fields, FIELD_WORKER_ID)),
      processId = ProcessId(requireField(fields, FIELD_PROCESS_ID)),
      queues = queues,
      recordedAt = Instant.parse(requireField(fields, FIELD_RECORDED_AT)),
      expiresAt = Instant.parse(requireField(fields, FIELD_EXPIRES_AT)),
    )
  }

  fun registeredProcessIds(): Set<ProcessId> =
    setAccessor.members(RedisKeyspace.processRegistry()).map(::ProcessId).toSet()

  private fun requireField(
    fields: Map<String, String>,
    name: String,
  ): String =
    fields[name]?.takeIf { it.isNotBlank() }
      ?: throw IllegalArgumentException("Redis heartbeat field '$name' is required.")

  companion object {
    private const val FIELD_WORKER_ID = "workerId"
    private const val FIELD_PROCESS_ID = "processId"
    private const val FIELD_QUEUES = "queues"
    private const val FIELD_RECORDED_AT = "recordedAt"
    private const val FIELD_EXPIRES_AT = "expiresAt"
  }
}
