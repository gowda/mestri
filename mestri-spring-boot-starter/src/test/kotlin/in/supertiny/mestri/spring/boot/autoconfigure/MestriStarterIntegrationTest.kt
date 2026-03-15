package `in`.supertiny.mestri.spring.boot.autoconfigure

import `in`.supertiny.mestri.core.middleware.ServerChain
import `in`.supertiny.mestri.core.middleware.ServerExecutionOutcome
import `in`.supertiny.mestri.core.middleware.ServerExecutionRequest
import `in`.supertiny.mestri.core.middleware.ServerExecutionResult
import `in`.supertiny.mestri.core.middleware.ServerMiddleware
import `in`.supertiny.mestri.core.model.InvocationArgument
import `in`.supertiny.mestri.core.model.InvocationDescriptor
import `in`.supertiny.mestri.core.model.InvocationMethod
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import `in`.supertiny.mestri.core.model.SpringBeanTarget
import `in`.supertiny.mestri.core.runtime.JobHandlerInvoker
import `in`.supertiny.mestri.core.runtime.JobLease
import `in`.supertiny.mestri.core.runtime.JobReservation
import `in`.supertiny.mestri.core.runtime.ServerExecutionPipeline
import `in`.supertiny.mestri.core.runtime.WorkerId
import `in`.supertiny.mestri.core.serialization.JobPayload
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@SpringBootTest(
  classes = [MestriStarterIntegrationTest.TestApplication::class],
  properties = [
    "mestri.worker.enabled=false",
    "mestri.scheduler.enabled=false",
  ],
)
class MestriStarterIntegrationTest {
  @Autowired
  private lateinit var jobHandlerInvoker: JobHandlerInvoker

  @Autowired
  private lateinit var serverExecutionPipeline: ServerExecutionPipeline

  @Autowired
  private lateinit var invocationRecorder: InvocationRecorder

  @Test
  fun `boot context resolves and invokes sample job bean`() {
    jobHandlerInvoker.invoke(
      reservation = sampleReservation(),
      request =
        ServerExecutionRequest(
          jobId = JobId("job-boot-123"),
          invocation = sampleReservation().payload.invocation,
          queue = QueueName("default"),
          attempt = 1,
        ),
    )

    assertEquals("invoice-42", invocationRecorder.lastInvoiceId)
    assertEquals(3, invocationRecorder.lastRetryCount)
  }

  @Test
  fun `boot context server pipeline applies ordered middleware around sample job bean`() {
    val result = serverExecutionPipeline.execute(sampleReservation())

    assertEquals(ServerExecutionOutcome.SUCCEEDED, result.outcome)
    assertEquals(
      mapOf(
        "boot-middleware-order" to "highest>low",
      ),
      result.metadata,
    )
    assertEquals("invoice-42", invocationRecorder.lastInvoiceId)
    assertEquals(3, invocationRecorder.lastRetryCount)
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  class TestApplication {
    @Bean
    fun stringRedisTemplate(): StringRedisTemplate =
      StringRedisTemplate(mock(RedisConnectionFactory::class.java))

    @Bean
    fun invocationRecorder(): InvocationRecorder = InvocationRecorder()

    @Bean("sampleJobs")
    fun sampleJobs(invocationRecorder: InvocationRecorder): SampleJobs =
      SampleJobs(invocationRecorder)

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun highestServerMiddleware(): ServerMiddleware = OrderedServerMiddleware(nextValue = "highest")

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    fun lowServerMiddleware(): ServerMiddleware = OrderedServerMiddleware(nextValue = "low")
  }

  class InvocationRecorder {
    var lastInvoiceId: String? = null
    var lastRetryCount: Int? = null
  }

  class SampleJobs(
    private val invocationRecorder: InvocationRecorder,
  ) {
    fun process(
      invoiceId: String,
      retryCount: Int,
    ) {
      invocationRecorder.lastInvoiceId = invoiceId
      invocationRecorder.lastRetryCount = retryCount
    }
  }

  class OrderedServerMiddleware(
    private val nextValue: String,
  ) : ServerMiddleware {
    override fun invoke(
      request: ServerExecutionRequest,
      chain: ServerChain,
    ): ServerExecutionResult =
      chain.proceed(
        request.copy(
          metadata =
            request.metadata +
              mapOf(
                "boot-middleware-order" to
                  listOfNotNull(
                    request.metadata["boot-middleware-order"],
                    nextValue,
                  ).joinToString(">"),
              ),
        ),
      )
  }

  private fun sampleReservation(): JobReservation =
    JobReservation(
      lease =
        JobLease(
          jobId = JobId("job-boot-123"),
          workerId = WorkerId("worker-boot-1"),
          acquiredAt = Instant.parse("2026-03-15T12:00:00Z"),
          expiresAt = Instant.parse("2026-03-15T12:05:00Z"),
        ),
      payload =
        JobPayload(
          jobId = JobId("job-boot-123"),
          invocation =
            InvocationDescriptor(
              target =
                SpringBeanTarget(
                  className = SampleJobs::class.java.name,
                  beanName = "sampleJobs",
                ),
              method =
                InvocationMethod(
                  name = "process",
                  parameterTypeNames = listOf("java.lang.String", "int"),
                ),
              arguments =
                listOf(
                  InvocationArgument("java.lang.String", "\"invoice-42\""),
                  InvocationArgument("int", "3"),
                ),
            ),
          queue = QueueName("default"),
        ),
      reservedAt = Instant.parse("2026-03-15T12:00:00Z"),
    )
}
