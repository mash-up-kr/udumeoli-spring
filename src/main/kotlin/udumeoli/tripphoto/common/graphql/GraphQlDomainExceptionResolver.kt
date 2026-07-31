package udumeoli.tripphoto.common.graphql

import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component

@Component
class GraphQlDomainExceptionResolver : DataFetcherExceptionResolverAdapter() {
    override fun resolveToSingleError(
        ex: Throwable,
        env: DataFetchingEnvironment,
    ): GraphQLError? {
        if (ex !is GraphQlDomainException) {
            return null
        }

        return GraphqlErrorBuilder
            .newError(env)
            .message(ex.message)
            .errorType(ErrorType.DataFetchingException)
            .extensions(mapOf("code" to ex.code.name))
            .build()
    }
}
