package `in`.supertiny.mestri.redis

import org.springframework.data.redis.core.StringRedisTemplate

class SpringRedisSetAccessor(
  private val redisTemplate: StringRedisTemplate,
) : RedisSetAccessor {
  override fun add(
    key: String,
    value: String,
  ) {
    redisTemplate.opsForSet().add(key, value)
  }

  override fun members(key: String): Set<String> = redisTemplate.opsForSet().members(key).orEmpty()

  override fun remove(
    key: String,
    value: String,
  ) {
    redisTemplate.opsForSet().remove(key, value)
  }
}
