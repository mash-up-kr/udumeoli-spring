package udumeoli.tripphoto.auth.graphql

import graphql.schema.DataFetchingEnvironment
import org.springframework.core.MethodParameter
import org.springframework.graphql.data.method.HandlerMethodArgumentResolver
import org.springframework.stereotype.Component
import udumeoli.tripphoto.auth.annotation.LoginUser
import udumeoli.tripphoto.auth.service.CurrentUserProvider

@Component
class LoginUserArgumentResolver(
    private val currentUserProvider: CurrentUserProvider,
) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(LoginUser::class.java) &&
            parameter.parameterType in supportedParameterTypes

    override fun resolveArgument(
        parameter: MethodParameter,
        environment: DataFetchingEnvironment,
    ): Any = currentUserProvider.requireCurrentUserId()

    companion object {
        private val supportedParameterTypes = setOf(Long::class.java, Long::class.javaObjectType)
    }
}
