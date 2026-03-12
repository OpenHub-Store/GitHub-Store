package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import zed.rainxch.core.domain.model.RateLimitInfo

interface RateLimitRepository {
    val rateLimitState: StateFlow<RateLimitInfo?>
    val rateLimitExhaustedEvent: SharedFlow<RateLimitInfo>

    fun updateRateLimit(rateLimitInfo: RateLimitInfo?)

    fun getCurrentRateLimit(): RateLimitInfo?

    fun isCurrentlyLimited(): Boolean

    fun clear()
}
