package `in`.supertiny.mestri.core.model

sealed interface InvocationTarget {
  val className: String
}

data class SpringBeanTarget(
  override val className: String,
  val beanName: String,
) : InvocationTarget {
  init {
    require(className.isNotBlank()) { "Spring invocation target className must not be blank." }
    require(beanName.isNotBlank()) { "Spring invocation target beanName must not be blank." }
  }
}

data class StandaloneHandlerTarget(
  override val className: String,
  val handlerId: String,
) : InvocationTarget {
  init {
    require(className.isNotBlank()) { "Standalone invocation target className must not be blank." }
    require(handlerId.isNotBlank()) { "Standalone invocation target handlerId must not be blank." }
  }
}
