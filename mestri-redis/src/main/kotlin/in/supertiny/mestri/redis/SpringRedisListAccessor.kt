package `in`.supertiny.mestri.redis

import org.springframework.data.redis.core.StringRedisTemplate

class SpringRedisListAccessor(
  private val redisTemplate: StringRedisTemplate,
) : RedisListAccessor {

  override fun rightPush(key: String, value: String) {
    redisTemplate.opsForList().rightPush(key, value)
  }
}
