package udumeoli.tripphoto.trip.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.dto.toPayload
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.party.service.PartyQueryService
import udumeoli.tripphoto.region.dto.toPayload
import udumeoli.tripphoto.region.repository.RegionRepository
import udumeoli.tripphoto.trip.dto.TravelStatsPayload
import udumeoli.tripphoto.trip.dto.TripPayload
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.repository.TripImageRepository
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.dto.toPayload
import udumeoli.tripphoto.user.service.UserService
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class TripQueryService(
    private val tripRepository: TripRepository,
    private val tripImageRepository: TripImageRepository,
    private val imageRepository: ImageRepository,
    private val regionRepository: RegionRepository,
    private val userService: UserService,
    private val partyQueryService: PartyQueryService,
) {
    @Transactional(readOnly = true)
    fun trips(
        currentUserId: Long,
        partyId: Long,
    ): List<TripPayload> {
        partyQueryService.requireMember(partyId, currentUserId)
        return toPayloads(tripRepository.findAllByPartyId(partyId).sortedWith(LATEST_FIRST))
    }

    @Transactional(readOnly = true)
    fun tripsByRegion(
        currentUserId: Long,
        partyId: Long,
        regionCode: String,
    ): List<TripPayload> {
        partyQueryService.requireMember(partyId, currentUserId)
        return toPayloads(
            tripRepository.findAllByPartyIdAndRegionCode(partyId, regionCode).sortedWith(LATEST_FIRST),
        )
    }

    @Transactional(readOnly = true)
    fun trip(
        currentUserId: Long,
        tripId: Long,
    ): TripPayload {
        val trip = requireTrip(tripId)
        partyQueryService.requireMember(trip.partyId, currentUserId)
        return toPayload(trip)
    }

    @Transactional(readOnly = true)
    fun travelStats(
        currentUserId: Long,
        partyId: Long,
    ): TravelStatsPayload {
        partyQueryService.requireMember(partyId, currentUserId)
        val trips = tripRepository.findAllByPartyId(partyId)
        return TravelStatsPayload(
            tripCount = trips.size,
            regionCount = trips.map { it.regionCode }.distinct().size,
            totalTravelDays =
                trips.sumOf { (ChronoUnit.DAYS.between(it.startDate, it.endDate) + 1).toInt() },
            firstTripDate = trips.minOfOrNull { it.startDate },
            lastTripDate = trips.maxOfOrNull { it.endDate },
        )
    }

    fun toPayload(trip: Trip): TripPayload = toPayloads(listOf(trip)).single()

    fun toPayloads(trips: List<Trip>): List<TripPayload> {
        if (trips.isEmpty()) {
            return emptyList()
        }

        val regionsByCode =
            regionRepository
                .findAllById(trips.map { it.regionCode }.distinct())
                .associateBy { it.regionCode }

        val tripImages = tripImageRepository.findAllByTripIdIn(trips.map { requireNotNull(it.id) })
        val imagesById =
            imageRepository
                .findAllById(tripImages.map { it.imageId }.distinct())
                .associateBy { requireNotNull(it.id) }
        val tripImagesByTripId = tripImages.groupBy { it.tripId }

        val userIds =
            (trips.mapNotNull { it.createdBy } + imagesById.values.mapNotNull { it.uploaderId }).distinct()
        val usersById = userService.findAllById(userIds).associateBy { requireNotNull(it.id) }

        return trips.map { trip ->
            val tripId = requireNotNull(trip.id)
            val region =
                regionsByCode[trip.regionCode]
                    ?: throw GraphQlDomainException(
                        GraphQlErrorCode.REGION_NOT_FOUND,
                        "지역을 찾을 수 없습니다: ${trip.regionCode}",
                    )
            val images =
                tripImagesByTripId[tripId]
                    .orEmpty()
                    .sortedBy { it.id }
                    .mapNotNull { imagesById[it.imageId] }
                    .map { image -> image.toPayload(image.uploaderId?.let(usersById::get)) }

            TripPayload(
                id = tripId,
                region = region.toPayload(),
                color = requireNotNull(trip.color) { "여행 색상이 없습니다: tripId=$tripId" },
                startDate = trip.startDate,
                endDate = trip.endDate,
                images = images,
                createdBy = trip.createdBy?.let(usersById::get)?.toPayload(),
                createdAt = requireNotNull(trip.auditMetadata.createdAt),
            )
        }
    }

    fun requireTrip(tripId: Long): Trip =
        tripRepository.findById(tripId).orElseThrow {
            GraphQlDomainException(GraphQlErrorCode.TRIP_NOT_FOUND, "여행을 찾을 수 없습니다.")
        }

    companion object {
        private val LATEST_FIRST: Comparator<Trip> =
            compareByDescending<Trip> { it.startDate }
                .thenByDescending { it.auditMetadata.createdAt ?: LocalDateTime.MIN }
    }
}
