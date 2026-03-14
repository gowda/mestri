package `in`.supertiny.mestri.redis

fun interface RedisHashLookup {
  fun entries(key: String): Map<String, String>
}

fun interface RedisHashWriter {
  fun putAll(key: String, fields: Map<String, String>)
}

interface RedisHashAccessor : RedisHashLookup, RedisHashWriter
