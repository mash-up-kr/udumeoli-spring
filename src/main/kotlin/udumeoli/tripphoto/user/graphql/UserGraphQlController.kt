package udumeoli.tripphoto.user.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.ContextValue
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.common.graphql.requireCurrentUserId
import udumeoli.tripphoto.config.CurrentUserGraphQlInterceptor
import udumeoli.tripphoto.user.dto.UpdateProfileInput
import udumeoli.tripphoto.user.dto.UserPayload
import udumeoli.tripphoto.user.service.UserService

@Controller
class UserGraphQlController(
    private val userService: UserService,
) {
    @QueryMapping
    fun me(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
    ): UserPayload = userService.me(requireCurrentUserId(currentUserId))

    @MutationMapping
    fun updateProfile(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument input: UpdateProfileInput,
    ): UserPayload =
        userService.updateProfile(
            currentUserId = requireCurrentUserId(currentUserId),
            nickname = input.nickname,
            profileImageUrl = input.profileImageUrl,
        )
}
