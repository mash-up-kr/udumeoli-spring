package udumeoli.tripphoto.trip.service

import org.springframework.stereotype.Service
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.trip.entity.TripImage
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripImageRepository
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import java.time.LocalDate

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

class TripRecordBundle(
    private val records: List<TripRecord>,
    tripImages: List<TripImage>,
    private val imagesById: Map<Long, Image>,
) {
    private val recordsByTripId = records.groupBy { it.tripId }
    private val tripImagesByRecordId = tripImages.groupBy { it.tripRecordId }
    private val tripImagesByTripId =
        records
            .associate { requireNotNull(it.id) to it.tripId }
            .let { tripIdByRecordId -> tripImages.groupBy { tripIdByRecordId.getValue(it.tripRecordId) } }
    private val tripIdsByMemberId = records.groupBy({ it.serviceUserId }, { it.tripId })

    /** 기록을 남긴 멤버 id (팟을 떠난 멤버도 포함될 수 있다) */
    val recordedMemberIds: Set<Long> = records.mapTo(mutableSetOf()) { it.serviceUserId }

    val uploaderIds: Set<Long> = imagesById.values.mapNotNullTo(mutableSetOf()) { it.uploaderId }

    fun recordsOf(tripId: Long): List<TripRecord> = recordsByTripId[tripId].orEmpty()

    fun tripImagesOfRecord(recordId: Long): List<TripImage> = tripImagesByRecordId[recordId].orEmpty()

    /** 한 여행에 팟 전원이 올린 사진 */
    fun tripImagesOfTrip(tripId: Long): List<TripImage> = tripImagesByTripId[tripId].orEmpty()

    /** 내가 기록을 남긴 여행 id */
    fun recordedTripIdsOf(userId: Long): Set<Long> = tripIdsByMemberId[userId].orEmpty().toSet()

    /** 촬영일 최신순으로 정렬해 Image로 바꾼다. */
    fun toImages(images: List<TripImage>): List<Image> = images.sortedWith(LATEST).mapNotNull(::imageOf)

    private fun imageOf(tripImage: TripImage): Image? = imagesById[tripImage.imageId]

    companion object {
        val EMPTY = TripRecordBundle(emptyList(), emptyList(), emptyMap())

        // 촬영일 미상은 맨 뒤로, 동률이면 나중에 등록된 사진이 앞으로
        private val LATEST: Comparator<TripImage> =
            compareByDescending<TripImage> { it.imageDate ?: LocalDate.MIN }
                .thenByDescending { it.id ?: 0L }
    }
}
