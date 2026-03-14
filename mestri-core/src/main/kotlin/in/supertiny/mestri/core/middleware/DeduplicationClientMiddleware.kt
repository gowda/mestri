package `in`.supertiny.mestri.core.middleware

import `in`.supertiny.mestri.core.model.DeduplicationKey
import `in`.supertiny.mestri.core.model.DeduplicationPolicy

class DeduplicationClientMiddleware(
  private val keyResolver: (ClientEnqueueRequest) -> DeduplicationKey?,
  private val policyResolver: (ClientEnqueueRequest) -> DeduplicationPolicy?,
) : ClientMiddleware {
  override fun invoke(
    request: ClientEnqueueRequest,
    chain: ClientChain,
  ): ClientEnqueueResult {
    val deduplicationKey = request.deduplicationKey ?: keyResolver(request)
    val deduplicationPolicy = request.deduplicationPolicy ?: policyResolver(request)

    return chain.proceed(
      request.copy(
        deduplicationKey = deduplicationKey,
        deduplicationPolicy = deduplicationPolicy,
      ),
    )
  }
}
