package udumeoli.tripphoto.image.dto

import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.user.dto.UserPayload
import udumeoli.tripphoto.user.dto.toPayload
import udumeoli.tripphoto.user.entity.ServiceUser
import java.time.LocalDateTime

data class ImagePayload(
    val id: Long,
    val originalUrl: String,
    val thumbnailUrl: String?,
    val uploader: UserPayload?,
    val createdAt: LocalDateTime,
)

fun Image.toPayload(
    uploader: ServiceUser?,
    apiBaseUrl: String,
): ImagePayload =
    ImagePayload(
        id = requireNotNull(id),
        originalUrl = "${apiBaseUrl.trimEnd('/')}/api/images/$id",
        thumbnailUrl = "${apiBaseUrl.trimEnd('/')}/api/images/$id/thumbnail",
        uploader = uploader?.toPayload(),
        createdAt = requireNotNull(createdAt),
    )
