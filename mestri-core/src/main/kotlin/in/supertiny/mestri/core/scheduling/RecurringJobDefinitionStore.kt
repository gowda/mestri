package `in`.supertiny.mestri.core.scheduling

import `in`.supertiny.mestri.core.model.RecurringJobDefinition
import `in`.supertiny.mestri.core.model.RecurringJobId

interface RecurringJobDefinitionStore {
  fun save(definition: RecurringJobDefinition)

  fun find(id: RecurringJobId): RecurringJobDefinition?

  fun findAll(): List<RecurringJobDefinition>
}
