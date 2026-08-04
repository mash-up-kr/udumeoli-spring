package udumeoli.tripphoto.auth.service

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode

@Component
class CurrentUserProvider {
    fun requireCurrentUserId(): Long =
        SecurityContextHolder
            .getContext()
            .authentication
            ?.takeIf { it.isAuthenticated }
            ?.name
            ?.toLongOrNull()
            ?: throw GraphQlDomainException(GraphQlErrorCode.UNAUTHENTICATED, "로그인이 필요합니다.")
}
