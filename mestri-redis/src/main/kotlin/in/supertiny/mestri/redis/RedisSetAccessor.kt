package `in`.supertiny.mestri.redis

fun interface RedisSetAdder {
  fun add(
    key: String,
    value: String,
  )
}

fun interface RedisSetMembersLookup {
  fun members(key: String): Set<String>
}

fun interface RedisSetRemover {
  fun remove(
    key: String,
    value: String,
  )
}

interface RedisSetAccessor :
  RedisSetAdder,
  RedisSetMembersLookup,
  RedisSetRemover
