package udumeoli.tripphoto.trip.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.dto.toPayload
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.party.service.PartyQueryService
import udumeoli.tripphoto.trip.dto.RegionMemberSlotPayload
import udumeoli.tripphoto.trip.dto.TripPayload
import udumeoli.tripphoto.trip.dto.TripRecordPayload
import udumeoli.tripphoto.trip.dto.TripStatsPayload
import udumeoli.tripphoto.trip.dto.VisitedRegionPayload
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.dto.toPayload
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.service.UserService
import java.time.LocalDateTime

/**
 * 핀 조회 — 목록·지역별·집계 전부.
 *
 * 모든 진입점이 [memberTrips]로 시작해(권한 확인 + 핀 로딩) [assemble]로 끝난다.
 * 응답 조립 규칙 자체는 이 파일 아래쪽 최상위 함수들에 모아 뒀다.
 */
@Service
class TripQueryService(
    private val tripRepository: TripRepository,
    private val tripRecordReader: TripRecordReader,
    private val userService: UserService,
    private val partyQueryService: PartyQueryService,
    @Value("\${app.api-base-url}") private val apiBaseUrl: String,
) {
    @Transactional(readOnly = true)
    fun trips(
        currentUserId: Long,
        partyId: Long,
    ): List<TripPayload> = assemble(currentUserId, memberTrips(currentUserId, partyId).sortedWith(LATEST_FIRST))

    /** 지역 상세 화면. 그 지역에 아무도 기록하지 않았으면 핀이 없으므로 null. */
    @Transactional(readOnly = true)
    fun tripInRegion(
        currentUserId: Long,
        partyId: Long,
        regionCode: String,
    ): TripPayload? {
        partyQueryService.requireMember(partyId, currentUserId)
        val trip = tripRepository.findByPartyIdAndRegionCode(partyId, regionCode) ?: return null
        return assemble(currentUserId, listOf(trip)).single()
    }

    @Transactional(readOnly = true)
    fun tripStats(
        currentUserId: Long,
        partyId: Long,
    ): TripStatsPayload = TripStatsPayload(regionCount = memberTrips(currentUserId, partyId).size)

    /** 팟이 다녀온 지역 목록 — 여행 앨범 진입 화면. */
    @Transactional(readOnly = true)
    fun visitedRegions(
        currentUserId: Long,
        partyId: Long,
    ): List<VisitedRegionPayload> {
        val trips = memberTrips(currentUserId, partyId)
        if (trips.isEmpty()) {
            return emptyList()
        }

        val bundle = tripRecordReader.read(trips.ids())
        val myTripIds = bundle.recordedTripIdsOf(currentUserId)
        // 자리는 팟 멤버 전원 몫이라, 사진을 올린 적 없는 멤버까지 읽어야 한다.
        val memberIds = partyQueryService.memberUserIdsInJoinOrder(partyId)
        val usersById = usersById(memberIds + bundle.uploaderIds)
        val members = memberIds.mapNotNull { usersById[it] }

        // 지역마다 핀이 하나라 그룹으로 묶을 게 없다 — 핀 하나가 곧 카드 하나다.
        return trips.sortedWith(LATEST_FIRST).map { trip ->
            val tripId = requireNotNull(trip.id)
            val slots = memberSlots(members, bundle.latestImageByMember(listOf(tripId)), usersById, apiBaseUrl)

            VisitedRegionPayload(
                regionCode = trip.regionCode,
                memberCount = members.size,
                recordedMemberCount = slots.count { it.image != null },
                slots = slots,
                hasUnrecordedTrip = tripId !in myTripIds,
            )
        }
    }

    /** 기록 뮤테이션 응답용. */
    fun toPayload(
        currentUserId: Long,
        trip: Trip,
    ): TripPayload = assemble(currentUserId, listOf(trip)).single()

    fun requireTrip(tripId: Long): Trip =
        tripRepository.findById(tripId).orElseThrow {
            GraphQlDomainException(GraphQlErrorCode.TRIP_NOT_FOUND, "핀을 찾을 수 없습니다.")
        }

    /** 모든 조회의 첫 관문 — 멤버인지 확인하고 팟의 핀을 읽는다. */
    private fun memberTrips(
        currentUserId: Long,
        partyId: Long,
    ): List<Trip> {
        partyQueryService.requireMember(partyId, currentUserId)
        return tripRepository.findAllByPartyId(partyId)
    }

    private fun assemble(
        currentUserId: Long,
        trips: List<Trip>,
    ): List<TripPayload> {
        if (trips.isEmpty()) {
            return emptyList()
        }

        val bundle = tripRecordReader.read(trips.ids())
        val memberIdsByPartyId =
            trips
                .map { it.partyId }
                .distinct()
                .associateWith(partyQueryService::memberUserIdsInJoinOrder)
        // 기록을 남긴 사람과 사진 업로더는 팟을 떠났을 수 있어 현재 멤버 목록만으로는 부족하다
        val usersById =
            usersById(memberIdsByPartyId.values.flatten() + bundle.recordedMemberIds + bundle.uploaderIds)

        return trips.map { trip ->
            val tripId = requireNotNull(trip.id)
            TripPayload(
                id = tripId,
                regionCode = trip.regionCode,
                records =
                    buildRecords(
                        currentUserId = currentUserId,
                        memberUserIds = memberIdsByPartyId.getValue(trip.partyId),
                        tripId = tripId,
                        bundle = bundle,
                        usersById = usersById,
                        apiBaseUrl = apiBaseUrl,
                    ),
                createdAt = requireNotNull(trip.auditMetadata.createdAt),
            )
        }
    }

    private fun usersById(userIds: Collection<Long>): Map<Long, ServiceUser> =
        userService.findAllById(userIds.distinct()).associateBy { requireNotNull(it.id) }
}

/**
 * 핀 1개의 records — 저장된 기록만이 아니라 **팟 멤버 전원**으로 채운다.
 * 아직 안 올린 멤버는 recorded=false 인 빈 행이 되고, 순서는 "나 최상단 → 팟 가입 순서"다.
 */
@Suppress("LongParameterList")
private fun buildRecords(
    currentUserId: Long,
    memberUserIds: List<Long>,
    tripId: Long,
    bundle: TripRecordBundle,
    usersById: Map<Long, ServiceUser>,
    apiBaseUrl: String,
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
            // 사진이 아직 없는 placeholder 행이면 null이 된다.
            val image = record?.let { bundle.imageOfRecord(requireNotNull(it.id)) }

            TripRecordPayload(
                member = member.toPayload(),
                recorded = record != null,
                keyword = record?.keyword,
                comment = record?.comment,
                image = image?.toPayloadWith(usersById, apiBaseUrl),
            )
        }
}

/**
 * 지역 카드의 자리를 만든다 — 팟 멤버 전원이 가입 순서대로 한 자리씩 갖고, 사진이 없으면 빈 자리다.
 * 자리 순서가 곧 "누구 자리인지"를 나타내기 때문에, 사진이 있는 멤버만 추려서는 안 된다.
 */
private fun memberSlots(
    members: List<ServiceUser>,
    imageByMemberId: Map<Long, Image>,
    usersById: Map<Long, ServiceUser>,
    apiBaseUrl: String,
): List<RegionMemberSlotPayload> =
    members.map { member ->
        RegionMemberSlotPayload(
            member = member.toPayload(),
            image = imageByMemberId[requireNotNull(member.id)]?.toPayloadWith(usersById, apiBaseUrl),
        )
    }

/** 목록 노출 순서 — 나중에 찍힌 핀이 위로. 여행 날짜가 없어져 등록 시각이 유일한 시간 축이다. */
private val LATEST_FIRST: Comparator<Trip> =
    compareByDescending<Trip> { it.auditMetadata.createdAt ?: LocalDateTime.MIN }
        .thenByDescending { it.id ?: 0L }

private fun List<Trip>.ids(): List<Long> = map { requireNotNull(it.id) }

private fun Image.toPayloadWith(
    usersById: Map<Long, ServiceUser>,
    apiBaseUrl: String,
) = toPayload(uploaderId?.let(usersById::get), apiBaseUrl)
