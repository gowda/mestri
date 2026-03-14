package `in`.supertiny.mestri.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

data class RetentionPolicy(
  val global: Duration = DEFAULT_GLOBAL_RETENTION,
  val succeeded: Duration? = null,
  val deleted: Duration? = null,
  val dead: Duration? = null,
  val retry: Duration? = null,
) {
  init {
    require(global.isPositive()) { "Global retention must be positive." }
    listOf(succeeded, deleted, dead, retry).forEach { duration ->
      require(duration == null || duration.isPositive()) { "Bucket retention must be positive when set." }
    }
  }

  fun retentionFor(bucket: JobBucket): Duration =
    when (bucket) {
      JobBucket.SUCCEEDED -> succeeded ?: global
      JobBucket.DELETED -> deleted ?: global
      JobBucket.DEAD -> dead ?: global
      JobBucket.RETRY -> retry ?: global
      else -> global
    }

  companion object {
    val DEFAULT_GLOBAL_RETENTION: Duration = 7.days
    val DEFAULT: RetentionPolicy = RetentionPolicy()
  }
}
