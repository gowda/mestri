package `in`.supertiny.mestri.redis

import `in`.supertiny.mestri.core.runtime.JobReservation

data class RedisReservationResult(
  val reservation: JobReservation,
  val queueKey: String,
)
