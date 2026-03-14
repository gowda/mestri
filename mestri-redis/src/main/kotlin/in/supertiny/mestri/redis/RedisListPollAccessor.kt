package `in`.supertiny.mestri.redis

fun interface RedisListPoller {
  fun leftPop(key: String): String?
}

interface RedisQueueAccessor :
  RedisListAccessor,
  RedisListPoller
