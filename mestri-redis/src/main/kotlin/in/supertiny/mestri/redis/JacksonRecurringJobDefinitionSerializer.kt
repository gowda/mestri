package `in`.supertiny.mestri.redis

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import `in`.supertiny.mestri.core.model.InvocationTarget
import `in`.supertiny.mestri.core.model.RecurringJobDefinition
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.model.StandaloneHandlerTarget

class JacksonRecurringJobDefinitionSerializer(
  private val objectMapper: ObjectMapper =
    jacksonObjectMapper()
      .findAndRegisterModules()
      .registerModule(JavaTimeModule())
      .registerModule(JacksonJobPayloadSerializer.DurationModule)
      .addMixIn(InvocationTarget::class.java, InvocationTargetMixin::class.java),
) {
  fun serialize(definition: RecurringJobDefinition): String =
    objectMapper.writeValueAsString(definition)

  fun deserialize(value: String): RecurringJobDefinition =
    objectMapper.readValue(value, RecurringJobDefinition::class.java)

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
  @JsonSubTypes(
    JsonSubTypes.Type(value = SpringBeanTarget::class, name = "spring"),
    JsonSubTypes.Type(value = StandaloneHandlerTarget::class, name = "standalone"),
  )
  private interface InvocationTargetMixin
}
