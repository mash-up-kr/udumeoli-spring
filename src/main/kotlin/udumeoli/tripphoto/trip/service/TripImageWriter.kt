package udumeoli.tripphoto.trip.service

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.trip.dto.TripImageInput
import udumeoli.tripphoto.trip.entity.TripImage
import udumeoli.tripphoto.trip.event.TripImagesChangedEvent
import udumeoli.tripphoto.trip.repository.TripImageRepository

/** 기록 1건의 사진 목록을 전달받은 목록으로 통째 교체한다. */
@Service
class TripImageWriter(
    private val tripImageRepository: TripImageRepository,
    private val imageService: ImageService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun setImages(
        tripRecordId: Long,
        images: List<TripImageInput>,
    ) {
        val requestedImageIds = images.map { it.imageId }
        val loadedImages = imageService.getImages(requestedImageIds)
        val previous = tripImageRepository.findAllByTripRecordId(tripRecordId)
        val previousImageIds = previous.mapTo(mutableSetOf()) { it.imageId }

        val requestedImageIdSet = requestedImageIds.toSet()
        val detached = previous.filterNot { it.imageId in requestedImageIdSet }
        val attached = images.filterNot { it.imageId in previousImageIds }
        if (detached.isEmpty() && attached.isEmpty()) {
            return
        }

        val attachedImageIds = attached.mapTo(mutableSetOf()) { it.imageId }
        tripImageRepository.deleteAll(detached)
        tripImageRepository.saveAll(
            attached.map {
                TripImage(
                    tripRecordId = tripRecordId,
                    imageId = it.imageId,
                    imageDate = it.takenAt,
                )
            },
        )
        eventPublisher.publishEvent(
            TripImagesChangedEvent(
                attachedImages = loadedImages.filter { requireNotNull(it.id) in attachedImageIds },
                detachedImageIds = detached.map { it.imageId },
            ),
        )
    }
}
