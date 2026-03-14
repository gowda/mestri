package `in`.supertiny.mestri.core.scheduling

import `in`.supertiny.mestri.core.model.RecurringJobDefinition
import java.time.Instant

class RecurringJobScheduler(
  private val store: RecurringJobDefinitionStore,
  private val clock: () -> Instant = { Instant.now() },
) {
  fun register(
    definition: RecurringJobDefinition,
    now: Instant = clock(),
  ): RecurringJobDefinition {
    val nextRunAt =
      if (definition.enabled) {
        CronScheduleCalculator.nextRunAt(definition, now)
      } else {
        null
      }
    val scheduledDefinition =
      definition.copy(
        updatedAt = now,
        nextRunAt = nextRunAt,
      )
    store.save(scheduledDefinition)
    return scheduledDefinition
  }

  fun dueDefinitions(now: Instant = clock()): List<RecurringJobDefinition> =
    store
      .findAll()
      .filter { definition ->
        definition.enabled &&
          definition.nextRunAt != null &&
          !definition.nextRunAt.isAfter(now)
      }.sortedBy { it.nextRunAt }

  fun markEnqueued(
    definition: RecurringJobDefinition,
    enqueuedAt: Instant = clock(),
  ): RecurringJobDefinition {
    val updatedDefinition =
      definition.copy(
        updatedAt = enqueuedAt,
        lastEnqueuedAt = enqueuedAt,
        nextRunAt =
          if (definition.enabled) {
            CronScheduleCalculator.nextRunAt(definition, enqueuedAt)
          } else {
            null
          },
      )
    store.save(updatedDefinition)
    return updatedDefinition
  }
}
