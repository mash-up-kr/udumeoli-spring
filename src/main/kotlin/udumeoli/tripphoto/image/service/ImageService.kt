package udumeoli.tripphoto.image.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import udumeoli.tripphoto.common.error.DomainException
import udumeoli.tripphoto.common.error.ErrorCode
import udumeoli.tripphoto.config.StorageProperties
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.image.storage.S3StorageAdapter
import udumeoli.tripphoto.image.thumbnail.HttpThumbnailAdapter
import java.util.UUID

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val storageAdapter: S3StorageAdapter,
    private val thumbnailAdapter: HttpThumbnailAdapter,
    private val properties: StorageProperties,
) {
    fun createUploadUrl(
        contentType: String,
        uploaderId: Long? = null,
    ): ImageUploadTarget {
        val extension =
            ALLOWED_CONTENT_TYPES[contentType]
                ?: throw DomainException(
                    ErrorCode.VALIDATION_ERROR,
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
            imageId = image.id!!,
            uploadUrl = storageAdapter.createUploadUrl(objectKey, contentType),
        )
    }

    fun verifyUploaded(imageIds: List<Long>): List<Image> {
        val imagesById = imageRepository.findAllById(imageIds).associateBy { it.id!! }
        val missingIds = imageIds.filterNot(imagesById::containsKey)
        if (missingIds.isNotEmpty()) {
            throw DomainException(ErrorCode.IMAGE_NOT_FOUND, "존재하지 않는 이미지: $missingIds")
        }
        val images = imageIds.map(imagesById::getValue)
        images.forEach(::verifyStoredObject)
        return images
    }

    fun requestThumbnails(images: List<Image>) {
        images.forEach { image ->
            runCatching { thumbnailAdapter.requestThumbnail(image.id!!, image.originalUrl) }
                .onFailure { log.warn("썸네일 생성 요청 실패: imageId={}", image.id, it) }
        }
    }

    fun deleteImages(imageIds: Collection<Long>) {
        if (imageIds.isEmpty()) {
            return
        }
        val images = imageRepository.findAllById(imageIds)
        deleteFromStorage(images)
        imageRepository.deleteAllById(images.map { it.id!! })
    }

    private fun deleteFromStorage(images: List<Image>) {
        storageAdapter.delete(images.flatMap { listOfNotNull(it.objectKey, thumbnailKeyOf(it)) })
    }

    private fun thumbnailKeyOf(image: Image): String? {
        val thumbnailUrl = image.thumbnailUrl ?: return null
        val prefix = "${properties.publicBaseUrl.trimEnd('/')}/"
        return thumbnailUrl.removePrefix(prefix).takeIf { it != thumbnailUrl }
    }

    private fun verifyStoredObject(image: Image) {
        val meta =
            storageAdapter.head(image.objectKey)
                ?: throw DomainException(ErrorCode.IMAGE_NOT_UPLOADED, "원본이 업로드되지 않은 이미지: ${image.id}")
        if (meta.contentLength > properties.upload.maxSizeBytes) {
            storageAdapter.delete(listOf(image.objectKey))
            throw DomainException(
                ErrorCode.VALIDATION_ERROR,
                "파일 크기 초과: ${meta.contentLength} bytes (최대 ${properties.upload.maxSizeBytes} bytes)",
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImageService::class.java)

        private val ALLOWED_CONTENT_TYPES =
            mapOf(
                "image/jpeg" to "jpg",
                "image/png" to "png",
                "image/webp" to "webp",
            )
    }
}

data class ImageUploadTarget(
    val imageId: Long,
    val uploadUrl: String,
)
