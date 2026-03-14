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

  override fun range(
    key: String,
    start: Long,
    end: Long,
  ): List<String> =
    redisTemplate
      .opsForZSet()
      .range(key, start, end)
      .orEmpty()
      .toList()

  override fun rangeByScore(
    key: String,
    minScoreInclusive: Double,
    maxScoreInclusive: Double,
  ): List<String> =
    redisTemplate
      .opsForZSet()
      .rangeByScore(key, minScoreInclusive, maxScoreInclusive)
      .orEmpty()
      .toList()

  override fun remove(
    key: String,
    value: String,
  ) {
    redisTemplate.opsForZSet().remove(key, value)
  }
}
