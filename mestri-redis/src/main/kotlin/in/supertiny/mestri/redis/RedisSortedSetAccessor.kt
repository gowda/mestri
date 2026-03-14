package `in`.supertiny.mestri.redis

fun interface RedisSortedSetWriter {
  fun add(
    key: String,
    value: String,
    score: Double,
  )
}

fun interface RedisSortedSetRangeLookup {
  fun range(
    key: String,
    start: Long,
    end: Long,
  ): List<String>
}

fun interface RedisSortedSetScoreRangeLookup {
  fun rangeByScore(
    key: String,
    minScoreInclusive: Double,
    maxScoreInclusive: Double,
  ): List<String>
}

fun interface RedisSortedSetRemover {
  fun remove(
    key: String,
    value: String,
  )
}

interface RedisSortedSetAccessor :
  RedisSortedSetWriter,
  RedisSortedSetRangeLookup,
  RedisSortedSetScoreRangeLookup,
  RedisSortedSetRemover
