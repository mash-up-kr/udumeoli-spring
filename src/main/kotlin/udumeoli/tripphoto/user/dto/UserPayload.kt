package udumeoli.tripphoto.user.dto

import udumeoli.tripphoto.user.entity.ServiceUser

data class UserPayload(
    val uid: Long,
    val nickname: String,
    val profileImage: Int,
)

fun ServiceUser.toPayload(): UserPayload =
    UserPayload(
        uid = requireNotNull(id),
        nickname = nickname,
        profileImage = profileImage,
    )
