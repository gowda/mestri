package `in`.supertiny.mestri.redis

import org.springframework.data.redis.core.StringRedisTemplate

class SpringRedisQueueAccessor(
  private val redisTemplate: StringRedisTemplate,
) : RedisQueueAccessor {
  override fun rightPush(
    key: String,
    value: String,
  ) {
    redisTemplate.opsForList().rightPush(key, value)
  }

  override fun leftPop(key: String): String? = redisTemplate.opsForList().leftPop(key)
}
