package udumeoli.tripphoto.trip.event

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import udumeoli.tripphoto.image.service.ImageService

@Component
class TripImageEventListener(
    private val imageService: ImageService,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onTripImagesChanged(event: TripImagesChangedEvent) {
        imageService.requestThumbnails(event.attachedImages)
        imageService.deleteImages(event.detachedImageIds)
    }
}
