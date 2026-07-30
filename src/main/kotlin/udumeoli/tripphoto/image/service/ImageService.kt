package udumeoli.tripphoto.image.service

import org.springframework.stereotype.Service
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository

@Service
class ImageService(
    private val imageRepository: ImageRepository,
) {
    fun getImages(imageIds: List<Long>): List<Image> {
        val imagesById = imageRepository.findAllById(imageIds).associateBy { requireNotNull(it.id) }
        val missingIds = imageIds.filterNot(imagesById::containsKey)
        if (missingIds.isNotEmpty()) {
            throw GraphQlDomainException(
                GraphQlErrorCode.IMAGE_NOT_FOUND,
                "존재하지 않는 이미지입니다: $missingIds",
            )
        }
        return imageIds.map(imagesById::getValue)
    }

    @Suppress("UnusedParameter")
    fun requestThumbnails(images: List<Image>) = Unit

    fun deleteImages(imageIds: Collection<Long>) {
        if (imageIds.isEmpty()) {
            return
        }
        imageRepository.deleteAllById(imageIds)
    }
}
