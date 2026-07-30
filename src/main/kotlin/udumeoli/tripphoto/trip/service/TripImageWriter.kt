package udumeoli.tripphoto.trip.service

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.trip.entity.TripImage
import udumeoli.tripphoto.trip.event.TripImagesChangedEvent
import udumeoli.tripphoto.trip.repository.TripImageRepository

@Service
class TripImageWriter(
    private val tripImageRepository: TripImageRepository,
    private val imageService: ImageService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun setImages(
        tripId: Long,
        imageIds: List<Long>,
    ) {
        val images = imageService.getImages(imageIds)
        val previous = tripImageRepository.findAllByTripId(tripId)
        val previousImageIds = previous.mapTo(mutableSetOf()) { it.imageId }
        val requestedImageIds = imageIds.toSet()

        val detached = previous.filterNot { it.imageId in requestedImageIds }
        val attachedImageIds = imageIds.filterNot { it in previousImageIds }
        if (detached.isEmpty() && attachedImageIds.isEmpty()) {
            return
        }

        tripImageRepository.deleteAll(detached)
        tripImageRepository.saveAll(
            attachedImageIds.map { TripImage(tripId = tripId, imageId = it) },
        )
        eventPublisher.publishEvent(
            TripImagesChangedEvent(
                attachedImages = images.filter { requireNotNull(it.id) in attachedImageIds },
                detachedImageIds = detached.map { it.imageId },
            ),
        )
    }
}
