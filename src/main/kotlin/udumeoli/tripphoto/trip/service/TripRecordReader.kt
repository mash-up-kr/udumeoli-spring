package udumeoli.tripphoto.trip.service

import org.springframework.stereotype.Service
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.trip.repository.TripImageRepository
import udumeoli.tripphoto.trip.repository.TripRecordRepository

@Service
class TripRecordReader(
    private val tripRecordRepository: TripRecordRepository,
    private val tripImageRepository: TripImageRepository,
    private val imageRepository: ImageRepository,
) {
    fun read(tripIds: Collection<Long>): TripRecordBundle {
        val records =
            if (tripIds.isEmpty()) {
                emptyList()
            } else {
                tripRecordRepository.findAllByTripIdIn(tripIds.distinct())
            }
        val recordIds = records.map { requireNotNull(it.id) }
        if (recordIds.isEmpty()) {
            return TripRecordBundle.EMPTY
        }

        val tripImages = tripImageRepository.findAllByTripRecordIdIn(recordIds)
        val imagesById =
            imageRepository
                .findAllById(tripImages.map { it.imageId }.distinct())
                .associateBy { requireNotNull(it.id) }

        return TripRecordBundle(records, tripImages, imagesById)
    }
}
