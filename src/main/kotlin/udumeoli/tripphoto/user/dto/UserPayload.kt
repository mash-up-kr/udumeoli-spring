package udumeoli.tripphoto.user.dto

import udumeoli.tripphoto.user.entity.ServiceUser

data class UserPayload(
    val id: Long,
    val nickname: String,
    val profileImageUrl: String,
)

fun ServiceUser.toPayload(): UserPayload =
    UserPayload(
        id = requireNotNull(id),
        nickname = nickname,
        profileImageUrl = profileImageUrl,
    )
