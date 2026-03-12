package zed.rainxch.core.domain.model

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class RateLimitInfo(
    val limit: Int,
    val remaining: Int,
    val resetTimestamp: Long,
    val resource: String = "core",
) {
    val isExhausted: Boolean
        get() = remaining == 0

    fun timeUntilReset(): Duration {
        val reset = Instant.fromEpochSeconds(resetTimestamp)
        return (reset - Clock.System.now()).coerceAtLeast(Duration.ZERO)
    }

    fun isCurrentlyLimited(): Boolean = isExhausted && timeUntilReset() > Duration.ZERO
}
