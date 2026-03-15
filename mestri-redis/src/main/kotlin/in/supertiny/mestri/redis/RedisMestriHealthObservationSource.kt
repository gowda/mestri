package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.health.JobBucketDepths
import `in`.supertiny.mestri.core.health.MestriHealthObservation
import `in`.supertiny.mestri.core.health.MestriHealthObservationSource
import `in`.supertiny.mestri.core.health.QueueDepth
import `in`.supertiny.mestri.core.health.RedisHealth
import `in`.supertiny.mestri.core.health.SchedulerHealth
import `in`.supertiny.mestri.core.health.WorkerHealth
import `in`.supertiny.mestri.core.model.JobId
import `in`.supertiny.mestri.core.model.QueueName
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant

class RedisMestriHealthObservationSource(
  private val redisTemplate: StringRedisTemplate,
  private val jobRecordStore: RedisJobRecordStore,
  private val leaseStore: RedisJobLeaseStore,
  private val heartbeatStore: RedisWorkerHeartbeatStore,
  private val queues: List<QueueName>,
  private val schedulerEnabled: Boolean,
  private val promoterEnabled: Boolean,
  private val clock: Clock = Clock.systemUTC(),
) : MestriHealthObservationSource {
  override fun observe(now: Instant): MestriHealthObservation {
    val pingResult = pingRedis()
    if (pingResult.isFailure) {
      val message = pingResult.exceptionOrNull()?.message

      return MestriHealthObservation(
        checkedAt = now,
        redis =
          RedisHealth(
            reachable = false,
            checkedAt = now,
            errorMessage = message,
          ),
        workers = WorkerHealth(activeWorkers = 0),
        scheduler =
          SchedulerHealth(
            schedulerEnabled = schedulerEnabled,
            promoterEnabled = promoterEnabled,
            checkedAt = now,
          ),
        details =
          buildMap {
            put("healthSource", "redis")
            message?.takeIf { it.isNotBlank() }?.let { put("redisError", it) }
          },
      )
    }

    val heartbeats = heartbeatStore.findAll()
    val leases = leaseStore.findAll()

    return MestriHealthObservation(
      checkedAt = now,
      redis = RedisHealth(reachable = true, checkedAt = now),
      workers =
        WorkerHealth(
          activeWorkers = heartbeats.count { heartbeat -> !heartbeat.isStale(now) },
          staleWorkers = heartbeats.count { heartbeat -> heartbeat.isStale(now) },
          staleLeases = leases.count { lease -> lease.isExpired(now) },
        ),
      queues = queues.map { queue -> queueDepth(queue, now) },
      buckets =
        JobBucketDepths(
          scheduledJobs = bucketSize(RedisKeyspace.scheduled()),
          retryJobs = bucketSize(RedisKeyspace.retry()),
          deadJobs = bucketSize(RedisKeyspace.dead()),
          succeededJobs = bucketSize(RedisKeyspace.succeeded()),
          deletedJobs = bucketSize(RedisKeyspace.deleted()),
        ),
      scheduler =
        SchedulerHealth(
          schedulerEnabled = schedulerEnabled,
          promoterEnabled = promoterEnabled,
          checkedAt = now,
        ),
      details = mapOf("healthSource" to "redis"),
    )
  }

  fun observe(): MestriHealthObservation = observe(Instant.now(clock))

  private fun pingRedis(): Result<Unit> =
    runCatching {
      redisTemplate.execute { connection ->
        connection.ping() ?: error("Redis ping returned null.")
      }
    }

  private fun queueDepth(
    queue: QueueName,
    now: Instant,
  ): QueueDepth {
    val queueKey = RedisKeyspace.queue(queue)
    val size = redisTemplate.opsForList().size(queueKey) ?: 0L
    val oldestJobId = redisTemplate.opsForList().index(queueKey, 0)

    return QueueDepth(
      queue = queue,
      enqueuedJobs = size,
      oldestJobAge =
        oldestJobId
          ?.let(::JobId)
          ?.let(jobRecordStore::find)
          ?.let { record ->
            Duration.between(record.createdAt, now).takeIf { age -> !age.isNegative }
          },
    )
  }

  private fun bucketSize(key: String): Long = redisTemplate.opsForZSet().zCard(key) ?: 0L
}
