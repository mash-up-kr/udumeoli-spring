package udumeoli.tripphoto.trip.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.dto.toPayload
import udumeoli.tripphoto.party.service.PartyQueryService
import udumeoli.tripphoto.trip.dto.TravelStatsPayload
import udumeoli.tripphoto.trip.dto.TripPayload
import udumeoli.tripphoto.trip.dto.TripRecordPayload
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.dto.toPayload
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.service.UserService
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 여행 조회.
 *
 * 핵심은 [assemble] — records를 저장된 기록만 내려주는 게 아니라 **팟 멤버 전원**으로 채운다.
 * 아직 안 올린 멤버는 recorded=false 인 빈 행이 되고, 순서는 "나 최상단 → 팟 가입 순서"다.
 */
@Service
class TripQueryService(
    private val tripRepository: TripRepository,
    private val tripRecordReader: TripRecordReader,
    private val userService: UserService,
    private val partyQueryService: PartyQueryService,
) {
    @Transactional(readOnly = true)
    fun trips(
        currentUserId: Long,
        partyId: Long,
    ): List<TripPayload> {
        partyQueryService.requireMember(partyId, currentUserId)
        return assemble(currentUserId, tripRepository.findAllByPartyId(partyId).sortedWith(LATEST_FIRST))
    }

    @Transactional(readOnly = true)
    fun tripsByRegion(
        currentUserId: Long,
        partyId: Long,
        regionCode: String,
    ): List<TripPayload> {
        partyQueryService.requireMember(partyId, currentUserId)
        val trips = tripRepository.findAllByPartyIdAndRegionCode(partyId, regionCode)
        return assemble(currentUserId, trips.sortedWith(LATEST_FIRST))
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

    fun toPayload(
        currentUserId: Long,
        trip: Trip,
    ): TripPayload = assemble(currentUserId, listOf(trip)).single()

    fun requireTrip(tripId: Long): Trip =
        tripRepository.findById(tripId).orElseThrow {
            GraphQlDomainException(GraphQlErrorCode.TRIP_NOT_FOUND, "여행을 찾을 수 없습니다.")
        }

    private fun assemble(
        currentUserId: Long,
        trips: List<Trip>,
    ): List<TripPayload> {
        if (trips.isEmpty()) {
            return emptyList()
        }

        val bundle = tripRecordReader.read(trips.map { requireNotNull(it.id) })
        val memberIdsByPartyId =
            trips
                .map { it.partyId }
                .distinct()
                .associateWith(partyQueryService::memberUserIdsInJoinOrder)
        val visitSequences = visitSequences(memberIdsByPartyId.keys)
        val userIds = (memberIdsByPartyId.values.flatten() + bundle.recordedMemberIds + bundle.uploaderIds).distinct()
        val usersById = userService.findAllById(userIds).associateBy { requireNotNull(it.id) }

        return trips.map { trip ->
            val tripId = requireNotNull(trip.id)
            val records =
                buildRecords(
                    currentUserId = currentUserId,
                    memberUserIds = memberIdsByPartyId.getValue(trip.partyId),
                    tripId = tripId,
                    bundle = bundle,
                    usersById = usersById,
                )

            TripPayload(
                id = tripId,
                regionCode = trip.regionCode.toInt(),
                keyword = trip.keyword,
                startDate = trip.startDate,
                endDate = trip.endDate,
                visitSequence = visitSequences[tripId] ?: 1,
                records = records,
                createdAt = requireNotNull(trip.auditMetadata.createdAt),
            )
        }
    }

    private fun buildRecords(
        currentUserId: Long,
        memberUserIds: List<Long>,
        tripId: Long,
        bundle: TripRecordBundle,
        usersById: Map<Long, ServiceUser>,
    ): List<TripRecordPayload> {
        val recordsByUserId = bundle.recordsOf(tripId).associateBy { it.serviceUserId }
        // 팟을 떠난 뒤에도 기록은 남는다. 사진이 조용히 사라지지 않도록 현재 멤버 뒤에 붙인다.
        val formerMemberIds = (recordsByUserId.keys - memberUserIds.toSet()).sorted()

        return (memberUserIds + formerMemberIds)
            // 안정 정렬이라 "나"만 맨 앞으로 올라가고 나머지는 가입 순서를 유지한다
            .sortedByDescending { it == currentUserId }
            .mapNotNull { userId ->
                val member = usersById[userId] ?: return@mapNotNull null
                val record = recordsByUserId[userId]
                val images = record?.let { bundle.toImages(bundle.tripImagesOfRecord(requireNotNull(it.id))) }

                TripRecordPayload(
                    member = member.toPayload(),
                    recorded = record != null,
                    comment = record?.comment,
                    images =
                        images
                            .orEmpty()
                            .map { image -> image.toPayload(image.uploaderId?.let(usersById::get)) },
                )
            }
    }

    /** 팟별로 지역마다 방문 순서를 매겨 "N번째 방문"을 구한다. */
    private fun visitSequences(partyIds: Collection<Long>): Map<Long, Int> =
        partyIds
            .flatMap { partyId -> tripRepository.findAllByPartyId(partyId) }
            .groupBy { it.partyId to it.regionCode }
            .values
            .flatMap { regionTrips ->
                regionTrips
                    .sortedWith(CHRONOLOGICAL)
                    .mapIndexed { index, trip -> requireNotNull(trip.id) to index + 1 }
            }.toMap()

    companion object {
        private val LATEST_FIRST: Comparator<Trip> =
            compareByDescending<Trip> { it.startDate }
                .thenByDescending { it.auditMetadata.createdAt ?: LocalDateTime.MIN }

        private val CHRONOLOGICAL: Comparator<Trip> =
            compareBy<Trip> { it.startDate }.thenBy { it.id ?: 0L }
    }
}
