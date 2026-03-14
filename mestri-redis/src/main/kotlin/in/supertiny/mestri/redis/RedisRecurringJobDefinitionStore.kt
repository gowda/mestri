package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.model.RecurringJobDefinition
import `in`.supertiny.mestri.core.model.RecurringJobId
import `in`.supertiny.mestri.core.scheduling.RecurringJobDefinitionStore

class RedisRecurringJobDefinitionStore(
  private val hashAccessor: RedisHashAccessor,
  private val setAccessor: RedisSetAccessor,
  private val serializer: JacksonRecurringJobDefinitionSerializer =
    JacksonRecurringJobDefinitionSerializer(),
) : RecurringJobDefinitionStore {
  override fun save(definition: RecurringJobDefinition) {
    hashAccessor.putAll(
      RedisKeyspace.recurringDefinition(definition.id.value),
      mapOf(
        FIELD_ID to definition.id.value,
        FIELD_DEFINITION to serializer.serialize(definition),
      ),
    )
    setAccessor.add(RedisKeyspace.recurringDefinitions(), definition.id.value)
  }

  override fun find(id: RecurringJobId): RecurringJobDefinition? {
    val fields = hashAccessor.entries(RedisKeyspace.recurringDefinition(id.value))
    if (fields.isEmpty()) return null
    return fields[FIELD_DEFINITION]?.let(serializer::deserialize)
  }

  override fun findAll(): List<RecurringJobDefinition> =
    setAccessor
      .members(RedisKeyspace.recurringDefinitions())
      .sorted()
      .mapNotNull { definitionId -> find(RecurringJobId(definitionId)) }

  companion object {
    private const val FIELD_ID = "id"
    private const val FIELD_DEFINITION = "definition"
  }
}
