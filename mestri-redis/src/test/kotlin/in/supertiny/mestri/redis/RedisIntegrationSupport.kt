package `in`.supertiny.mestri.redis

import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers(disabledWithoutDocker = true)
abstract class RedisIntegrationSupport {

  protected fun newStringRedisTemplate(): StringRedisTemplate {
    val connectionFactory =
      LettuceConnectionFactory(
        RedisStandaloneConfiguration(redis.host, redis.firstMappedPort)
      )
    connectionFactory.afterPropertiesSet()

    return StringRedisTemplate(connectionFactory).also { it.afterPropertiesSet() }
  }

  protected fun newHashAccessor(redisTemplate: StringRedisTemplate): RedisHashAccessor =
    SpringRedisHashAccessor(redisTemplate)

  protected fun newSortedSetAccessor(redisTemplate: StringRedisTemplate): RedisSortedSetAccessor =
    SpringRedisSortedSetAccessor(redisTemplate)

  protected fun newSetAccessor(redisTemplate: StringRedisTemplate): RedisSetAccessor =
    SpringRedisSetAccessor(redisTemplate)

  protected fun newPayloadSerializer(): JacksonJobPayloadSerializer = JacksonJobPayloadSerializer()

  protected fun newJobRecordStore(redisTemplate: StringRedisTemplate): RedisJobRecordStore =
    RedisJobRecordStore(
      hashAccessor = newHashAccessor(redisTemplate),
      mapper = RedisJobRecordMapper(newPayloadSerializer()),
    )

  protected fun newLeaseStore(redisTemplate: StringRedisTemplate): RedisJobLeaseStore =
    RedisJobLeaseStore(newHashAccessor(redisTemplate))

  protected fun newHeartbeatStore(redisTemplate: StringRedisTemplate): RedisWorkerHeartbeatStore =
    RedisWorkerHeartbeatStore(
      hashAccessor = newHashAccessor(redisTemplate),
      setAccessor = newSetAccessor(redisTemplate),
    )

  protected fun flushRedis(redisTemplate: StringRedisTemplate) {
    redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
  }

  companion object {
    @Container
    @JvmStatic
    protected val redis: GenericContainer<*> =
      GenericContainer("redis:7.4-alpine").withExposedPorts(6379)
  }
}
