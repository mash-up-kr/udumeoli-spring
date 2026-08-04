package udumeoli.tripphoto.user.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.auth.annotation.LoginUser
import udumeoli.tripphoto.user.dto.UpdateProfileInput
import udumeoli.tripphoto.user.dto.UserPayload
import udumeoli.tripphoto.user.service.UserService

@Controller
class UserGraphQlController(
    private val userService: UserService,
) {
    @QueryMapping
    fun me(
        @LoginUser currentUserId: Long,
    ): UserPayload = userService.me(currentUserId)

    @MutationMapping
    fun updateProfile(
        @LoginUser currentUserId: Long,
        @Argument input: UpdateProfileInput,
    ): UserPayload =
        userService.updateProfile(
            currentUserId = currentUserId,
            nickname = input.nickname,
            profileImageUrl = input.profileImageUrl,
        )
}
