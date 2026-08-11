package udumeoli.tripphoto.user.dto

import udumeoli.tripphoto.user.entity.ServiceUser

data class UserPayload(
    val id: Long,
    val nickname: String,
    val profileImage: Long,
    val profileImageUrl: String? = null,
)

fun ServiceUser.toPayload(): UserPayload =
    UserPayload(
        id = requireNotNull(id),
        nickname = nickname,
        profileImage = profileImage,
    )
