package `in`.supertiny.mestri.spring.boot.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("mestri")
data class MestriProperties(
  val enabled: Boolean = true,
  val redis: Redis = Redis(),
  val worker: Worker = Worker(),
  val scheduler: Scheduler = Scheduler(),
  val dashboard: Dashboard = Dashboard(),
  val health: Health = Health(),
  val retention: Retention = Retention(),
  val retries: Retries = Retries(),
  val dedup: Dedup = Dedup(),
) {
  data class Redis(
    val host: String = "localhost",
    val port: Int = 6379,
    val database: Int = 0,
    val username: String? = null,
    val password: String? = null,
    val ssl: Boolean = false,
    val keyPrefix: String = "mestri",
  ) {
    init {
      require(host.isNotBlank()) { "mestri.redis.host must not be blank." }
      require(port in 1..65535) { "mestri.redis.port must be between 1 and 65535." }
      require(database >= 0) { "mestri.redis.database must be zero or greater." }
      require(keyPrefix.isNotBlank()) { "mestri.redis.keyPrefix must not be blank." }
    }
  }

  data class Worker(
    val enabled: Boolean = true,
    val queues: List<String> = listOf("default"),
    val concurrency: Int = 10,
    val shutdownTimeout: Duration = Duration.ofSeconds(30),
    val heartbeatInterval: Duration = Duration.ofSeconds(5),
    val leaseDuration: Duration = Duration.ofMinutes(5),
    val pollInterval: Duration = Duration.ofSeconds(1),
  ) {
    init {
      require(queues.isNotEmpty()) { "mestri.worker.queues must not be empty." }
      require(queues.none { it.isBlank() }) {
        "mestri.worker.queues must not contain blank values."
      }
      require(concurrency > 0) { "mestri.worker.concurrency must be greater than zero." }
      require(!shutdownTimeout.isNegative && !shutdownTimeout.isZero) {
        "mestri.worker.shutdownTimeout must be greater than zero."
      }
      require(!heartbeatInterval.isNegative && !heartbeatInterval.isZero) {
        "mestri.worker.heartbeatInterval must be greater than zero."
      }
      require(!leaseDuration.isNegative && !leaseDuration.isZero) {
        "mestri.worker.leaseDuration must be greater than zero."
      }
      require(!pollInterval.isNegative && !pollInterval.isZero) {
        "mestri.worker.pollInterval must be greater than zero."
      }
    }
  }

  data class Scheduler(
    val enabled: Boolean = true,
    val promotionInterval: Duration = Duration.ofSeconds(1),
    val recurringCheckInterval: Duration = Duration.ofSeconds(30),
  ) {
    init {
      require(!promotionInterval.isNegative && !promotionInterval.isZero) {
        "mestri.scheduler.promotionInterval must be greater than zero."
      }
      require(!recurringCheckInterval.isNegative && !recurringCheckInterval.isZero) {
        "mestri.scheduler.recurringCheckInterval must be greater than zero."
      }
    }
  }

  data class Dashboard(
    val enabled: Boolean = false,
    val path: String = "/mestri",
    val username: String? = null,
    val password: String? = null,
  ) {
    init {
      require(path.startsWith("/")) { "mestri.dashboard.path must start with '/'." }
    }
  }

  data class Health(
    val enabled: Boolean = true,
    val maxEnqueuedThreshold: Long? = null,
    val maxRetryThreshold: Long? = null,
    val maxDeadThreshold: Long? = null,
    val maxStaleLeaseThreshold: Long? = null,
  ) {
    init {
      require(maxEnqueuedThreshold == null || maxEnqueuedThreshold >= 0) {
        "mestri.health.maxEnqueuedThreshold must be zero or greater."
      }
      require(maxRetryThreshold == null || maxRetryThreshold >= 0) {
        "mestri.health.maxRetryThreshold must be zero or greater."
      }
      require(maxDeadThreshold == null || maxDeadThreshold >= 0) {
        "mestri.health.maxDeadThreshold must be zero or greater."
      }
      require(maxStaleLeaseThreshold == null || maxStaleLeaseThreshold >= 0) {
        "mestri.health.maxStaleLeaseThreshold must be zero or greater."
      }
    }
  }

  data class Retention(
    val global: BucketRetention = BucketRetention(maxEntries = 10_000),
    val succeeded: BucketRetention? = null,
    val deleted: BucketRetention? = null,
    val retry: BucketRetention? = null,
    val dead: BucketRetention? = null,
  )

  data class BucketRetention(
    val maxEntries: Long? = null,
    val maxAge: Duration? = null,
  ) {
    init {
      require(maxEntries == null || maxEntries >= 0) {
        "mestri.retention.maxEntries values must be zero or greater."
      }
      require(maxAge == null || (!maxAge.isNegative && !maxAge.isZero)) {
        "mestri.retention.maxAge values must be greater than zero."
      }
    }
  }

  data class Retries(
    val defaultAttempts: Int = 10,
    val initialDelay: Duration = Duration.ofMinutes(1),
    val maxDelay: Duration = Duration.ofHours(1),
    val backoffMultiplier: Double = 2.0,
    val jitter: Boolean = true,
  ) {
    init {
      require(defaultAttempts >= 0) { "mestri.retries.defaultAttempts must be zero or greater." }
      require(!initialDelay.isNegative && !initialDelay.isZero) {
        "mestri.retries.initialDelay must be greater than zero."
      }
      require(!maxDelay.isNegative && !maxDelay.isZero) {
        "mestri.retries.maxDelay must be greater than zero."
      }
      require(backoffMultiplier >= 1.0) {
        "mestri.retries.backoffMultiplier must be at least 1.0."
      }
    }
  }

  data class Dedup(
    val enabled: Boolean = true,
    val defaultEnqueueTtl: Duration = Duration.ofMinutes(5),
    val defaultScheduleTtl: Duration = Duration.ofHours(1),
  ) {
    init {
      require(!defaultEnqueueTtl.isNegative && !defaultEnqueueTtl.isZero) {
        "mestri.dedup.defaultEnqueueTtl must be greater than zero."
      }
      require(!defaultScheduleTtl.isNegative && !defaultScheduleTtl.isZero) {
        "mestri.dedup.defaultScheduleTtl must be greater than zero."
      }
    }
  }
}
