package `in`.supertiny.mestri.spring.boot.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.JobHandlerInvoker
import `in`.supertiny.mestri.core.runtime.JobReservation
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils
import java.lang.reflect.Method

class SpringBeanJobHandlerInvoker(
  private val applicationContext: ApplicationContext,
  private val objectMapper: ObjectMapper,
) : JobHandlerInvoker {
  override fun invoke(
    reservation: JobReservation,
    request: `in`.supertiny.mestri.core.middleware.ServerExecutionRequest,
  ) {
    val target =
      request.invocation.target as? SpringBeanTarget
        ?: throw IllegalArgumentException(
          "SpringBeanJobHandlerInvoker only supports SpringBeanTarget.",
        )
    val bean = applicationContext.getBean(target.beanName)
    val parameterTypes =
      request.invocation.method.parameterTypeNames
        .map(::resolveClass)
        .toTypedArray()
    val method = resolveMethod(bean, request.invocation.method.name, parameterTypes)
    val arguments =
      request.invocation.arguments
        .mapIndexed { index, argument ->
          deserializeArgument(argument, parameterTypes[index])
        }.toTypedArray()

    method.isAccessible = true
    method.invoke(bean, *arguments)
  }

  private fun resolveMethod(
    bean: Any,
    methodName: String,
    parameterTypes: Array<Class<*>>,
  ): Method {
    val parameterSignature =
      parameterTypes.joinToString(
        separator = ", ",
      ) { parameterType ->
        parameterType.name
      }

    return runCatching {
      bean.javaClass.getMethod(methodName, *parameterTypes)
    }.getOrElse {
      throw IllegalArgumentException(
        "No matching method '$methodName($parameterSignature)' found on bean '${bean.javaClass.name}'.",
        it,
      )
    }
  }

  private fun deserializeArgument(
    argument: InvocationArgument,
    parameterType: Class<*>,
  ): Any? {
    val serializedValue = argument.serializedValue ?: return null
    return objectMapper.readValue(serializedValue, parameterType)
  }

  private fun resolveClass(typeName: String): Class<*> =
    primitiveTypes[typeName]
      ?: ClassUtils.forName(typeName, applicationContext.classLoader)

  companion object {
    private val primitiveTypes =
      mapOf(
        "boolean" to Boolean::class.javaPrimitiveType!!,
        "byte" to Byte::class.javaPrimitiveType!!,
        "char" to Char::class.javaPrimitiveType!!,
        "double" to Double::class.javaPrimitiveType!!,
        "float" to Float::class.javaPrimitiveType!!,
        "int" to Int::class.javaPrimitiveType!!,
        "long" to Long::class.javaPrimitiveType!!,
        "short" to Short::class.javaPrimitiveType!!,
      )
  }
}
