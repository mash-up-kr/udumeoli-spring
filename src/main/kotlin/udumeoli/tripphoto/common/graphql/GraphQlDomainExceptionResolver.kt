package udumeoli.tripphoto.common.graphql

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component
import udumeoli.tripphoto.common.i18n.ApiMessageLocalizer

@Component
class GraphQlDomainExceptionResolver(
    private val messageLocalizer: ApiMessageLocalizer,
) : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(
        ex: Throwable,
        env: DataFetchingEnvironment,
    ): GraphQLError? =
        (ex as? GraphQlDomainException)?.let { domainEx ->
            GraphqlErrorBuilder
                .newError(env)
                .errorType(domainEx.code.toErrorType())
                .message(messageLocalizer.localize(domainEx.message, env.locale))
                .extensions(mapOf("code" to domainEx.code.name))
                .build()
        }
}

private fun GraphQlErrorCode.toErrorType(): ErrorType =
    when (this) {
        GraphQlErrorCode.UNAUTHENTICATED -> ErrorType.UNAUTHORIZED
        GraphQlErrorCode.FORBIDDEN -> ErrorType.FORBIDDEN
        GraphQlErrorCode.PARTY_NOT_FOUND,
        GraphQlErrorCode.MEMBER_NOT_FOUND,
        -> ErrorType.NOT_FOUND
        else -> ErrorType.BAD_REQUEST
    }
