package `in`.supertiny.mestri.redis

import org.springframework.data.redis.core.StringRedisTemplate

class SpringRedisSortedSetAccessor(
  private val redisTemplate: StringRedisTemplate,
) : RedisSortedSetAccessor {
  override fun add(
    key: String,
    value: String,
    score: Double,
  ) {
    redisTemplate.opsForZSet().add(key, value, score)
  }
}
