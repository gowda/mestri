package `in`.supertiny.mestri.redis

import org.springframework.data.redis.core.StringRedisTemplate

class SpringRedisHashAccessor(
  private val redisTemplate: StringRedisTemplate,
) : RedisHashAccessor {
  override fun entries(key: String): Map<String, String> =
    redisTemplate.opsForHash<String, String>().entries(key)

  override fun putAll(
    key: String,
    fields: Map<String, String>,
  ) {
    redisTemplate.opsForHash<String, String>().putAll(key, fields)
  }

  override fun delete(key: String) {
    redisTemplate.delete(key)
  }
}
