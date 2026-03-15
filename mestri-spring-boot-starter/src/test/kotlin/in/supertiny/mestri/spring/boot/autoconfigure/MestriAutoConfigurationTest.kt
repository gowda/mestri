package `in`.supertiny.mestri.spring.boot.autoconfigure

import `in`.supertiny.mestri.core.runtime.MaintenanceLoop
import `in`.supertiny.mestri.core.scheduling.RecurringJobDefinitionStore
import `in`.supertiny.mestri.core.scheduling.RecurringJobScheduler
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
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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

  @Configuration(proxyBeanMethods = false)
  private class TestRedisConfiguration {
    @Bean
    fun stringRedisTemplate(): StringRedisTemplate =
      StringRedisTemplate(mock(RedisConnectionFactory::class.java))
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

  private inline fun <reified T : Any> assertSingleBean(
    context: org.springframework.context.ApplicationContext,
  ) {
    assertTrue(context.getBeanNamesForType(T::class.java).size == 1)
  }
}
