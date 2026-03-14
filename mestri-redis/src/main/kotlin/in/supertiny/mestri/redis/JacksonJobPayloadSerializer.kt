package `in`.supertiny.mestri.redis

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import `in`.supertiny.mestri.core.model.InvocationTarget
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.model.StandaloneHandlerTarget
import `in`.supertiny.mestri.core.serialization.JobPayload
import `in`.supertiny.mestri.core.serialization.JobPayloadSerializer
import `in`.supertiny.mestri.core.serialization.PayloadFormat
import `in`.supertiny.mestri.core.serialization.SerializedPayload
import kotlin.time.Duration
import kotlin.time.Duration.Companion.parseIsoString

class JacksonJobPayloadSerializer(
  private val objectMapper: ObjectMapper =
    jacksonObjectMapper()
      .findAndRegisterModules()
      .registerModule(DurationModule)
      .addMixIn(InvocationTarget::class.java, InvocationTargetMixin::class.java),
) : JobPayloadSerializer {
  override fun serialize(value: JobPayload): SerializedPayload =
    SerializedPayload(
      format = PayloadFormat.JSON,
      content = objectMapper.writeValueAsString(value),
    )

  override fun deserialize(payload: SerializedPayload): JobPayload {
    require(payload.format == PayloadFormat.JSON) {
      "JacksonJobPayloadSerializer only supports JSON payloads."
    }
    return objectMapper.readValue(payload.content, JobPayload::class.java)
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
  @JsonSubTypes(
    JsonSubTypes.Type(value = SpringBeanTarget::class, name = "spring"),
    JsonSubTypes.Type(value = StandaloneHandlerTarget::class, name = "standalone"),
  )
  private interface InvocationTargetMixin

  internal object DurationModule : SimpleModule("mestri-duration-module") {
    init {
      addSerializer(Duration::class.java, DurationSerializer)
      addDeserializer(Duration::class.java, DurationDeserializer)
    }
  }

  private object DurationSerializer : StdSerializer<Duration>(Duration::class.java) {
    override fun serialize(
      value: Duration,
      gen: JsonGenerator,
      provider: SerializerProvider,
    ) {
      gen.writeString(value.toIsoString())
    }
  }

  private object DurationDeserializer : StdDeserializer<Duration>(Duration::class.java) {
    override fun deserialize(
      parser: JsonParser,
      ctxt: com.fasterxml.jackson.databind.DeserializationContext,
    ): Duration = parseIsoString(parser.valueAsString)
  }
}
