package udumeoli.tripphoto.common.graphql

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component
import udumeoli.tripphoto.common.error.DomainException
import udumeoli.tripphoto.common.error.ErrorCode

@Component
class GraphQlExceptionResolver : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(
        ex: Throwable,
        env: DataFetchingEnvironment,
    ): GraphQLError? =
        (ex as? DomainException)?.let { domainEx ->
            GraphqlErrorBuilder
                .newError(env)
                .errorType(domainEx.code.toErrorType())
                .message(domainEx.message)
                .extensions(mapOf("code" to domainEx.code.name))
                .build()
        }
}

private fun ErrorCode.toErrorType(): ErrorType =
    when (this) {
        ErrorCode.UNAUTHENTICATED -> ErrorType.UNAUTHORIZED
        ErrorCode.FORBIDDEN -> ErrorType.FORBIDDEN
        ErrorCode.PARTY_NOT_FOUND,
        ErrorCode.TRIP_NOT_FOUND,
        ErrorCode.IMAGE_NOT_FOUND,
        ErrorCode.REGION_NOT_FOUND,
        ErrorCode.MEMBER_NOT_FOUND,
        -> ErrorType.NOT_FOUND
        else -> ErrorType.BAD_REQUEST
    }
