package `in`.supertiny.mestri.redis

fun interface RedisListWriter {
  fun rightPush(
    key: String,
    value: String,
  )
}

interface RedisListAccessor : RedisListWriter
