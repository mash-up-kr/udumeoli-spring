package udumeoli.tripphoto.image.service

import org.springframework.stereotype.Service
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.dto.ImageUploadTarget
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.image.storage.S3StorageAdapter
import java.util.UUID

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val storageAdapter: S3StorageAdapter,
) {
    fun createUploadUrl(
        uploaderId: Long,
        contentType: String,
    ): ImageUploadTarget {
        val extension =
            ALLOWED_CONTENT_TYPES[contentType]
                ?: throw GraphQlDomainException(
                    GraphQlErrorCode.VALIDATION_ERROR,
                    "허용되지 않는 contentType: $contentType (허용: ${ALLOWED_CONTENT_TYPES.keys.joinToString()})",
                )
        val objectKey = "original/${UUID.randomUUID()}.$extension"
        val image =
            imageRepository.save(
                Image(
                    objectKey = objectKey,
                    originalUrl = storageAdapter.publicUrl(objectKey),
                    uploaderId = uploaderId,
                ),
            )
        return ImageUploadTarget(
            imageId = requireNotNull(image.id),
            uploadUrl = storageAdapter.createUploadUrl(objectKey, contentType),
        )
    }

    companion object {
        private val ALLOWED_CONTENT_TYPES =
            mapOf(
                "image/jpeg" to "jpg",
                "image/png" to "png",
                "image/webp" to "webp",
            )
    }
}
