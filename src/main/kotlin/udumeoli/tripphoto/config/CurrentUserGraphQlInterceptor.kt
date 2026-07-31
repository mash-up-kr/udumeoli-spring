package udumeoli.tripphoto.config

import org.springframework.graphql.server.WebGraphQlInterceptor
import org.springframework.graphql.server.WebGraphQlRequest
import org.springframework.graphql.server.WebGraphQlResponse
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class CurrentUserGraphQlInterceptor : WebGraphQlInterceptor {
    override fun intercept(
        request: WebGraphQlRequest,
        chain: WebGraphQlInterceptor.Chain,
    ): Mono<WebGraphQlResponse> {
        val currentUserId = request.headers.getFirst(CURRENT_USER_ID_HEADER)?.toLongOrNull()

        if (currentUserId != null) {
            request.configureExecutionInput { _, builder ->
                builder
                    .graphQLContext { context -> context.put(CURRENT_USER_ID_CONTEXT_KEY, currentUserId) }
                    .build()
            }
        }

        return chain.next(request)
    }

    companion object {
        const val CURRENT_USER_ID_HEADER = "X-User-Id"
        const val CURRENT_USER_ID_CONTEXT_KEY = "currentUserId"
    }
}
