package udumeoli.tripphoto.image.dto

data class ImageUploadTarget(
    val imageId: Long,
    val uploadUrl: String,
    val encryptionHeaders: List<HttpHeader>,
)

data class HttpHeader(
    val key: String,
    val value: String,
)
