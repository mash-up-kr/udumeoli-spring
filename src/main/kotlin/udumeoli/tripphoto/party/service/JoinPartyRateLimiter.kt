package udumeoli.tripphoto.party.service

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.common.i18n.MessageKeys
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@Component
class JoinPartyRateLimiter {
    private val attempts =
        Caffeine
            .newBuilder()
            .expireAfterWrite(WINDOW)
            .maximumSize(MAX_TRACKED_USERS)
            .build<Long, AtomicInteger>()

    fun check(currentUserId: Long) {
        val count = attempts.get(currentUserId) { AtomicInteger(0) }.incrementAndGet()
        if (count > MAX_ATTEMPTS_PER_WINDOW) {
            throw GraphQlDomainException(
                GraphQlErrorCode.RATE_LIMITED,
                MessageKeys.Invite.RATE_LIMITED,
            )
        }
    }

    companion object {
        private const val MAX_ATTEMPTS_PER_WINDOW = 10
        private const val MAX_TRACKED_USERS = 100_000L
        private val WINDOW: Duration = Duration.ofMinutes(1)
    }
}
