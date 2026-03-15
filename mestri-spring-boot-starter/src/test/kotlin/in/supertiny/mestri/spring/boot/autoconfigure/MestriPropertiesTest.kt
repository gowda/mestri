package `in`.supertiny.mestri.spring.boot.autoconfigure

import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MestriPropertiesTest {
  @Test
  fun `properties expose stable defaults`() {
    val properties = MestriProperties()

    assertTrue(properties.enabled)
    assertEquals("localhost", properties.redis.host)
    assertEquals(6379, properties.redis.port)
    assertEquals(listOf("default"), properties.worker.queues)
    assertEquals(10, properties.worker.concurrency)
    assertEquals(Duration.ofSeconds(30), properties.worker.shutdownTimeout)
    assertFalse(properties.dashboard.enabled)
    assertEquals("/mestri", properties.dashboard.path)
    assertEquals(10, properties.retries.defaultAttempts)
    assertEquals(Duration.ofMinutes(5), properties.dedup.defaultEnqueueTtl)
  }

  @Test
  fun `properties bind from mestri namespace`() {
    val source =
      MapConfigurationPropertySource(
        mapOf(
          "mestri.enabled" to "false",
          "mestri.redis.host" to "redis.internal",
          "mestri.redis.port" to "6380",
          "mestri.redis.database" to "2",
          "mestri.redis.key-prefix" to "custom",
          "mestri.worker.queues[0]" to "critical",
          "mestri.worker.queues[1]" to "default",
          "mestri.worker.concurrency" to "25",
          "mestri.worker.shutdown-timeout" to "45s",
          "mestri.worker.heartbeat-interval" to "7s",
          "mestri.worker.lease-duration" to "10m",
          "mestri.scheduler.recurring-check-interval" to "15s",
          "mestri.dashboard.enabled" to "true",
          "mestri.dashboard.path" to "/jobs",
          "mestri.health.max-enqueued-threshold" to "500",
          "mestri.retention.global.max-entries" to "15000",
          "mestri.retention.dead.max-age" to "30d",
          "mestri.retries.default-attempts" to "5",
          "mestri.retries.initial-delay" to "30s",
          "mestri.retries.backoff-multiplier" to "3.0",
          "mestri.dedup.default-enqueue-ttl" to "2m",
          "mestri.dedup.default-schedule-ttl" to "3h",
        ),
      )

    val binder = Binder(source)
    val properties = binder.bind("mestri", Bindable.of(MestriProperties::class.java)).get()

    assertFalse(properties.enabled)
    assertEquals("redis.internal", properties.redis.host)
    assertEquals(6380, properties.redis.port)
    assertEquals(2, properties.redis.database)
    assertEquals("custom", properties.redis.keyPrefix)
    assertEquals(listOf("critical", "default"), properties.worker.queues)
    assertEquals(25, properties.worker.concurrency)
    assertEquals(Duration.ofSeconds(45), properties.worker.shutdownTimeout)
    assertEquals(Duration.ofSeconds(7), properties.worker.heartbeatInterval)
    assertEquals(Duration.ofMinutes(10), properties.worker.leaseDuration)
    assertEquals(Duration.ofSeconds(15), properties.scheduler.recurringCheckInterval)
    assertTrue(properties.dashboard.enabled)
    assertEquals("/jobs", properties.dashboard.path)
    assertEquals(500, properties.health.maxEnqueuedThreshold)
    assertEquals(15_000, properties.retention.global.maxEntries)
    assertEquals(Duration.ofDays(30), properties.retention.dead?.maxAge)
    assertEquals(5, properties.retries.defaultAttempts)
    assertEquals(Duration.ofSeconds(30), properties.retries.initialDelay)
    assertEquals(3.0, properties.retries.backoffMultiplier)
    assertEquals(Duration.ofMinutes(2), properties.dedup.defaultEnqueueTtl)
    assertEquals(Duration.ofHours(3), properties.dedup.defaultScheduleTtl)
  }
}
