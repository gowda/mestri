package org.jobrunr.jobs.annotations

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Job(
  val name: String = "",
  val retries: Int = DEFAULT_RETRIES_UNSET,
) {
  companion object {
    const val DEFAULT_RETRIES_UNSET: Int = -1
  }
}
