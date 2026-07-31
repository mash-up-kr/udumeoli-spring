package udumeoli.tripphoto.trip.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.party.service.PartyQueryService
import udumeoli.tripphoto.region.repository.RegionRepository
import udumeoli.tripphoto.trip.dto.CreateTripInput
import udumeoli.tripphoto.trip.dto.TripPayload
import udumeoli.tripphoto.trip.dto.UpdateTripInput
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.repository.TripRepository
import java.time.LocalDate

/**
 * 여행 등록/수정/삭제. 입력 검증과 권한 확인을 하고 trip 행을 쓴다.
 */
@Service
class TripCommandService(
    private val tripRepository: TripRepository,
    private val regionRepository: RegionRepository,
    private val partyQueryService: PartyQueryService,
    private val tripQueryService: TripQueryService,
    private val tripImageWriter: TripImageWriter,
) {
    @Transactional
    fun createTrip(
        currentUserId: Long,
        input: CreateTripInput,
    ): TripPayload {
        partyQueryService.requireMember(input.partyId, currentUserId)
        requireSingleImage(input.imageIds)
        requireRegionExists(input.regionCode)
        validateColor(input.color)
        requirePeriod(input.startDate, input.endDate)

        val trip =
            tripRepository.save(
                Trip(
                    partyId = input.partyId,
                    regionCode = input.regionCode,
                    color = input.color,
                    startDate = input.startDate,
                    endDate = input.endDate,
                    createdBy = currentUserId,
                ),
            )
        tripImageWriter.setImages(requireNotNull(trip.id), input.imageIds)
        return tripQueryService.toPayload(trip)
    }

    @Transactional
    fun updateTrip(
        currentUserId: Long,
        input: UpdateTripInput,
    ): TripPayload {
        val trip = tripQueryService.requireTrip(input.tripId)
        requireCreatorOrOwner(trip, currentUserId)

        input.imageIds?.let { requireSingleImage(it) }
        input.regionCode?.let { requireRegionExists(it) }
        input.color?.let { validateColor(it) }
        val startDate = input.startDate ?: trip.startDate
        val endDate = input.endDate ?: trip.endDate
        requirePeriod(startDate, endDate)

        val updated =
            tripRepository.save(
                trip.copy(
                    regionCode = input.regionCode ?: trip.regionCode,
                    color = input.color ?: trip.color,
                    startDate = startDate,
                    endDate = endDate,
                ),
            )

        input.imageIds?.let { tripImageWriter.setImages(input.tripId, it) }
        return tripQueryService.toPayload(updated)
    }

    @Transactional
    fun deleteTrip(
        currentUserId: Long,
        tripId: Long,
    ): Long {
        val trip = tripQueryService.requireTrip(tripId)
        requireCreatorOrOwner(trip, currentUserId)

        tripImageWriter.setImages(tripId, emptyList())
        tripRepository.delete(trip)
        return tripId
    }

    private fun requireCreatorOrOwner(
        trip: Trip,
        currentUserId: Long,
    ) {
        partyQueryService.requireMember(trip.partyId, currentUserId)
        if (trip.createdBy == currentUserId) {
            return
        }
        if (!partyQueryService.isOwner(trip.partyId, currentUserId)) {
            throw GraphQlDomainException(
                GraphQlErrorCode.FORBIDDEN,
                "여행 등록자 또는 방장만 수정/삭제할 수 있습니다.",
            )
        }
    }

    private fun requireSingleImage(imageIds: List<Long>) {
        if (imageIds.size != 1) {
            throw GraphQlDomainException(
                GraphQlErrorCode.VALIDATION_ERROR,
                "여행 사진은 1장이어야 합니다. (전달: ${imageIds.size}장)",
            )
        }
    }

    private fun requireRegionExists(regionCode: String) {
        if (!regionRepository.existsById(regionCode)) {
            throw GraphQlDomainException(GraphQlErrorCode.REGION_NOT_FOUND, "존재하지 않는 지역입니다: $regionCode")
        }
    }

    private fun validateColor(color: String) {
        if (!COLOR_PATTERN.matches(color)) {
            throw GraphQlDomainException(
                GraphQlErrorCode.VALIDATION_ERROR,
                "색상은 #RRGGBB 형식의 hex 여야 합니다: $color",
            )
        }
    }

    private fun requirePeriod(
        startDate: LocalDate,
        endDate: LocalDate,
    ) {
        if (startDate.isAfter(endDate)) {
            throw GraphQlDomainException(
                GraphQlErrorCode.VALIDATION_ERROR,
                "여행 시작일은 종료일보다 늦을 수 없습니다.",
            )
        }
    }

    companion object {
        private val COLOR_PATTERN = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
