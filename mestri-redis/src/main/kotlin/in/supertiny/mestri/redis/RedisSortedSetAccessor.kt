package `in`.supertiny.mestri.redis

fun interface RedisSortedSetWriter {
  fun add(key: String, value: String, score: Double)
}

interface RedisSortedSetAccessor : RedisSortedSetWriter
