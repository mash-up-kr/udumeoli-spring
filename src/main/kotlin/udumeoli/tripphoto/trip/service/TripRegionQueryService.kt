package udumeoli.tripphoto.trip.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.image.dto.toPayload
import udumeoli.tripphoto.party.service.PartyQueryService
import udumeoli.tripphoto.trip.dto.RegionCardPayload
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.service.UserService

/**
 * 지역별 집계 — 여행 이미지 상세 보기 진입 화면의 카드 목록.
 */
@Service
class TripRegionQueryService(
    private val tripRepository: TripRepository,
    private val tripRecordReader: TripRecordReader,
    private val userService: UserService,
    private val partyQueryService: PartyQueryService,
) {
    @Transactional(readOnly = true)
    fun regionCards(
        currentUserId: Long,
        partyId: Long,
    ): List<RegionCardPayload> {
        partyQueryService.requireMember(partyId, currentUserId)

        val trips = tripRepository.findAllByPartyId(partyId)
        if (trips.isEmpty()) {
            return emptyList()
        }

        val bundle = tripRecordReader.read(trips.map { requireNotNull(it.id) })
        val myTripIds = bundle.recordedTripIdsOf(currentUserId)
        val usersById = userService.findAllById(bundle.uploaderIds).associateBy { requireNotNull(it.id) }

        return trips
            .groupBy { it.regionCode }
            .entries
            .sortedByDescending { (_, regionTrips) -> regionTrips.maxOf { it.startDate } }
            .map { (regionCode, regionTrips) ->
                val images =
                    bundle.toImages(
                        regionTrips.flatMap { bundle.tripImagesOfTrip(requireNotNull(it.id)) },
                    )

                RegionCardPayload(
                    regionCode = regionCode.toInt(),
                    visitCount = regionTrips.size,
                    images =
                        images
                            .take(STACK_IMAGE_LIMIT)
                            .map { image -> image.toPayload(image.uploaderId?.let(usersById::get)) },
                    totalImageCount = images.size,
                    hasUnrecordedTrip = regionTrips.any { requireNotNull(it.id) !in myTripIds },
                )
            }
    }

    companion object {
        /** 카드 스택에 내려주는 대표 사진 수. 나머지는 totalImageCount로 "+N" 처리한다. */
        private const val STACK_IMAGE_LIMIT = 5
    }
}
