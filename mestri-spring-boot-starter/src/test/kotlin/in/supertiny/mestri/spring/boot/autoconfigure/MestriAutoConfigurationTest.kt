package `in`.supertiny.mestri.spring.boot.autoconfigure

import `in`.supertiny.mestri.core.health.MestriHealthObservation
import `in`.supertiny.mestri.core.health.MestriHealthService
import `in`.supertiny.mestri.core.health.MestriHealthStatus
import `in`.supertiny.mestri.core.health.RedisHealth
import `in`.supertiny.mestri.core.health.SchedulerHealth
import `in`.supertiny.mestri.core.health.WorkerHealth
import `in`.supertiny.mestri.core.middleware.ClientChain
import `in`.supertiny.mestri.core.middleware.ClientEnqueueRequest
import `in`.supertiny.mestri.core.middleware.ClientEnqueueResult
import `in`.supertiny.mestri.core.middleware.ClientMiddleware
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
import `in`.supertiny.mestri.core.runtime.MaintenanceLoop
import `in`.supertiny.mestri.core.runtime.ServerExecutionPipeline
import `in`.supertiny.mestri.core.scheduling.RecurringJobDefinitionStore
import `in`.supertiny.mestri.core.scheduling.RecurringJobScheduler
import `in`.supertiny.mestri.core.serialization.JobPayload
import `in`.supertiny.mestri.core.serialization.JobPayloadSerializer
import `in`.supertiny.mestri.redis.RedisImmediateJobEnqueuer
import `in`.supertiny.mestri.redis.RedisJobRecordStore
import `in`.supertiny.mestri.redis.RedisMaintenanceExecutor
import `in`.supertiny.mestri.redis.RedisMaintenanceSnapshotSource
import `in`.supertiny.mestri.redis.RedisQueueReservationStore
import `in`.supertiny.mestri.redis.RedisScheduledJobPromoter
import `in`.supertiny.mestri.redis.RedisScheduledJobStore
import `in`.supertiny.mestri.redis.RedisTerminalJobStore
import org.mockito.Mockito.mock
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MestriAutoConfigurationTest {
  private val contextRunner =
    ApplicationContextRunner()
      .withConfiguration(
        AutoConfigurations.of(
          JacksonAutoConfiguration::class.java,
          MestriAutoConfiguration::class.java,
        ),
      ).withUserConfiguration(TestRedisConfiguration::class.java)

  @Test
  fun `auto configuration registers core redis runtime beans`() {
    contextRunner.run { context ->
      assertSingleBean<MestriProperties>(context)
      assertSingleBean<ClientMiddlewareRegistry>(context)
      assertSingleBean<JobHandlerInvoker>(context)
      assertSingleBean<JobPayloadSerializer>(context)
      assertSingleBean<RedisJobRecordStore>(context)
      assertSingleBean<RedisImmediateJobEnqueuer>(context)
      assertSingleBean<RedisScheduledJobStore>(context)
      assertSingleBean<RedisTerminalJobStore>(context)
      assertSingleBean<RedisQueueReservationStore>(context)
      assertSingleBean<RedisScheduledJobPromoter>(context)
      assertSingleBean<RedisMaintenanceSnapshotSource>(context)
      assertSingleBean<RedisMaintenanceExecutor>(context)
      assertSingleBean<MaintenanceLoop>(context)
      assertSingleBean<MestriWorkerLifecycle>(context)
      assertSingleBean<MestriSchedulerLifecycle>(context)
      assertSingleBean<MestriMaintenanceLifecycle>(context)
      assertSingleBean<ServerExecutionPipeline>(context)
      assertSingleBean<ServerMiddlewareRegistry>(context)
      assertSingleBean<RecurringJobDefinitionStore>(context)
      assertSingleBean<RecurringJobScheduler>(context)
    }
  }

  @Test
  fun `auto configuration backs off when mestri is disabled`() {
    contextRunner
      .withPropertyValues("mestri.enabled=false")
      .run { context ->
        kotlin.test.assertFalse(context.containsBean("mestriRedisJobRecordStore"))
        kotlin.test.assertFalse(context.containsBean("mestriRedisImmediateJobEnqueuer"))
      }
  }

  @Test
  fun `auto configuration backs off worker lifecycle when worker is disabled`() {
    contextRunner
      .withPropertyValues("mestri.worker.enabled=false")
      .run { context ->
        kotlin.test.assertFalse(context.containsBean("mestriWorkerLifecycle"))
        kotlin.test.assertFalse(context.containsBean("mestriMaintenanceLifecycle"))
      }
  }

  @Test
  fun `auto configuration backs off scheduler lifecycle when scheduler is disabled`() {
    contextRunner
      .withPropertyValues("mestri.scheduler.enabled=false")
      .run { context ->
        kotlin.test.assertFalse(context.containsBean("mestriSchedulerLifecycle"))
      }
  }

  @Test
  fun `auto configuration backs off for user supplied clock`() {
    contextRunner
      .withUserConfiguration(CustomClockConfiguration::class.java)
      .run { context ->
        assertEquals(
          Instant.parse("2026-03-15T12:00:00Z"),
          context.getBean(Clock::class.java).instant(),
        )
      }
  }

  @Test
  fun `job handler invoker resolves and invokes spring bean targets`() {
    contextRunner
      .withUserConfiguration(SampleJobConfiguration::class.java)
      .run { context ->
        val invoker = context.getBean(JobHandlerInvoker::class.java)
        val recorder = context.getBean(InvocationRecorder::class.java)

        invoker.invoke(
          reservation = sampleReservation(),
          request =
            `in`.supertiny.mestri.core.middleware.ServerExecutionRequest(
              jobId = JobId("job-123"),
              invocation = sampleReservation().payload.invocation,
              queue = QueueName("default"),
              attempt = 1,
            ),
        )

        assertEquals("invoice-42", recorder.lastInvoiceId)
        assertEquals(3, recorder.lastRetryCount)
      }
  }

  @Test
  fun `middleware beans are discovered in Spring order`() {
    contextRunner
      .withUserConfiguration(OrderedMiddlewareConfiguration::class.java)
      .run { context ->
        val clientRegistry = context.getBean(ClientMiddlewareRegistry::class.java)
        val serverRegistry = context.getBean(ServerMiddlewareRegistry::class.java)

        assertEquals(
          listOf("highest-client", "low-client"),
          clientRegistry.middlewares.map { middleware -> middleware.toString() },
        )
        assertEquals(
          listOf("highest-server", "low-server"),
          serverRegistry.middlewares.map { middleware -> middleware.toString() },
        )
      }
  }

  @Test
  fun `server execution pipeline uses ordered Spring server middlewares`() {
    contextRunner
      .withUserConfiguration(
        OrderedMiddlewareConfiguration::class.java,
        SampleJobConfiguration::class.java,
      ).run { context ->
        val pipeline = context.getBean(ServerExecutionPipeline::class.java)
        val result = pipeline.execute(sampleReservation())

        assertEquals(ServerExecutionOutcome.SUCCEEDED, result.outcome)
        assertEquals(
          mapOf(
            "middleware-order" to "highest>low",
          ),
          result.metadata,
        )
      }
  }

  @Test
  fun `health indicator is registered when a health service is available`() {
    contextRunner
      .withUserConfiguration(HealthServiceConfiguration::class.java)
      .run { context ->
        val healthIndicator = context.getBean(MestriHealthIndicator::class.java)
        val health = healthIndicator.health()

        assertEquals(Status("DEGRADED"), health.status)
        assertEquals(true, health.details["redisReachable"])
        assertEquals(2, health.details["activeWorkers"])
        assertEquals("configured threshold exceeded", health.details["reason"])
      }
  }

  @Test
  fun `health indicator backs off when health is disabled`() {
    contextRunner
      .withPropertyValues("mestri.health.enabled=false")
      .withUserConfiguration(HealthServiceConfiguration::class.java)
      .run { context ->
        kotlin.test.assertFalse(context.containsBean("mestriHealthIndicator"))
      }
  }

  @Configuration(proxyBeanMethods = false)
  private class TestRedisConfiguration {
    @Bean
    fun stringRedisTemplate(): StringRedisTemplate =
      StringRedisTemplate(mock(RedisConnectionFactory::class.java))
  }

  @Configuration(proxyBeanMethods = false)
  private class SampleJobConfiguration {
    @Bean
    fun invocationRecorder(): InvocationRecorder = InvocationRecorder()

    @Bean("sampleJobs")
    fun sampleJobs(recorder: InvocationRecorder): SampleJobs = SampleJobs(recorder)
  }

  @Configuration(proxyBeanMethods = false)
  private class CustomClockConfiguration {
    @Bean
    fun mestriClock(): Clock =
      Clock.fixed(
        Instant.parse("2026-03-15T12:00:00Z"),
        ZoneOffset.UTC,
      )
  }

  @Configuration(proxyBeanMethods = false)
  private class OrderedMiddlewareConfiguration {
    @Bean
    fun highestClientMiddleware(): ClientMiddleware =
      OrderedClientMiddleware(
        name = "highest-client",
        order = Ordered.HIGHEST_PRECEDENCE,
      )

    @Bean
    fun lowClientMiddleware(): ClientMiddleware =
      OrderedClientMiddleware(
        name = "low-client",
        order = Ordered.LOWEST_PRECEDENCE,
      )

    @Bean
    fun highestServerMiddleware(): ServerMiddleware =
      OrderedServerMiddleware(
        name = "highest-server",
        nextValue = "highest",
        order = Ordered.HIGHEST_PRECEDENCE,
      )

    @Bean
    fun lowServerMiddleware(): ServerMiddleware =
      OrderedServerMiddleware(
        name = "low-server",
        nextValue = "low",
        order = Ordered.LOWEST_PRECEDENCE,
      )
  }

  @Configuration(proxyBeanMethods = false)
  private class HealthServiceConfiguration {
    @Bean
    fun mestriHealthService(): MestriHealthService =
      MestriHealthService(
        observationSource = { now ->
          MestriHealthObservation(
            checkedAt = now,
            redis = RedisHealth(reachable = true, checkedAt = now),
            workers = WorkerHealth(activeWorkers = 2),
            scheduler =
              SchedulerHealth(
                schedulerEnabled = true,
                promoterEnabled = true,
                checkedAt = now,
              ),
            details = mapOf("reason" to "configured threshold exceeded"),
          )
        },
        statusEvaluator = { MestriHealthStatus.DEGRADED },
      )
  }

  private inline fun <reified T : Any> assertSingleBean(
    context: org.springframework.context.ApplicationContext,
  ) {
    assertTrue(context.getBeanNamesForType(T::class.java).size == 1)
  }

  private fun sampleReservation(): JobReservation =
    JobReservation(
      lease =
        JobLease(
          jobId = JobId("job-123"),
          workerId =
            `in`.supertiny.mestri.core.runtime
              .WorkerId("worker-1"),
          acquiredAt = Instant.parse("2026-03-15T12:00:00Z"),
          expiresAt = Instant.parse("2026-03-15T12:05:00Z"),
        ),
      payload =
        JobPayload(
          jobId = JobId("job-123"),
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

  class InvocationRecorder {
    var lastInvoiceId: String? = null
    var lastRetryCount: Int? = null
  }

  class SampleJobs(
    private val recorder: InvocationRecorder,
  ) {
    fun process(
      invoiceId: String,
      retryCount: Int,
    ) {
      recorder.lastInvoiceId = invoiceId
      recorder.lastRetryCount = retryCount
    }
  }

  private open class NamedClientMiddleware(
    private val name: String,
  ) : ClientMiddleware {
    override fun invoke(
      request: ClientEnqueueRequest,
      chain: ClientChain,
    ): ClientEnqueueResult = chain.proceed(request)

    override fun toString(): String = name
  }

  private class OrderedClientMiddleware(
    name: String,
    private val order: Int,
  ) : NamedClientMiddleware(name),
    Ordered {
    override fun getOrder(): Int = order
  }

  private open class NamedServerMiddleware(
    private val name: String,
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
                "middleware-order" to
                  listOfNotNull(request.metadata["middleware-order"], nextValue).joinToString(">"),
              ),
        ),
      )

    override fun toString(): String = name
  }

  private class OrderedServerMiddleware(
    name: String,
    nextValue: String,
    private val order: Int,
  ) : NamedServerMiddleware(name, nextValue),
    Ordered {
    override fun getOrder(): Int = order
  }
}
