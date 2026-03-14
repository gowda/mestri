package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.JobBucket
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.serialization.JobPayloadSerializer
import `in`.supertiny.mestri.core.serialization.PayloadFormat
import `in`.supertiny.mestri.core.serialization.SerializedPayload
import java.time.Instant

class RedisJobRecordMapper(
  private val payloadSerializer: JobPayloadSerializer,
) {
  fun toHash(record: RedisJobRecord): Map<String, String> {
    val serializedPayload = payloadSerializer.serialize(record.payload)
    return mapOf(
      FIELD_VERSION to CURRENT_VERSION.toString(),
      FIELD_JOB_ID to record.jobId.value,
      FIELD_BUCKET to record.bucket.name,
      FIELD_PAYLOAD_FORMAT to serializedPayload.format.name,
      FIELD_PAYLOAD to serializedPayload.content,
      FIELD_CREATED_AT to record.createdAt.toString(),
      FIELD_UPDATED_AT to record.updatedAt.toString(),
    )
  }

  fun fromHash(fields: Map<String, String>): RedisJobRecord {
    require(fields.isNotEmpty()) { "Redis job record fields must not be empty." }

    val payload =
      payloadSerializer.deserialize(
        SerializedPayload(
          format = PayloadFormat.valueOf(requireField(fields, FIELD_PAYLOAD_FORMAT)),
          content = requireField(fields, FIELD_PAYLOAD),
        ),
      )

    return RedisJobRecord(
      jobId = JobId(requireField(fields, FIELD_JOB_ID)),
      bucket = JobBucket.valueOf(requireField(fields, FIELD_BUCKET)),
      payload = payload,
      createdAt = Instant.parse(requireField(fields, FIELD_CREATED_AT)),
      updatedAt = Instant.parse(requireField(fields, FIELD_UPDATED_AT)),
    )
  }

  private fun requireField(
    fields: Map<String, String>,
    name: String,
  ): String =
    fields[name]?.takeIf { it.isNotBlank() }
      ?: throw IllegalArgumentException("Redis job record field '$name' is required.")

  companion object {
    const val CURRENT_VERSION: Int = 1
    const val FIELD_VERSION: String = "version"
    const val FIELD_JOB_ID: String = "jobId"
    const val FIELD_BUCKET: String = "bucket"
    const val FIELD_PAYLOAD_FORMAT: String = "payloadFormat"
    const val FIELD_PAYLOAD: String = "payload"
    const val FIELD_CREATED_AT: String = "createdAt"
    const val FIELD_UPDATED_AT: String = "updatedAt"
  }
}
