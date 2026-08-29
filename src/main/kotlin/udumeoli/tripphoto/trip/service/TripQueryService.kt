package udumeoli.tripphoto.trip.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.dto.toPayload
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.party.service.PartyQueryService
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
import java.time.temporal.ChronoUnit

/**
 * 여행 조회 — 목록·지역별·집계 전부.
 *
 * 모든 진입점이 [memberTrips]로 시작해(권한 확인 + 여행 로딩) [assemble]로 끝난다.
 * 응답 조립 규칙 자체는 이 파일 아래쪽 최상위 함수들에 모아 뒀다.
 */
@Service
class TripQueryService(
    private val tripRepository: TripRepository,
    private val tripRecordReader: TripRecordReader,
    private val userService: UserService,
    private val partyQueryService: PartyQueryService,
    @org.springframework.beans.factory.annotation.Value("\${app.api-base-url}") private val apiBaseUrl: String,
) {
    @Transactional(readOnly = true)
    fun trips(
        currentUserId: Long,
        partyId: Long,
    ): List<TripPayload> = toPayloads(currentUserId, memberTrips(currentUserId, partyId))

    @Transactional(readOnly = true)
    fun tripsByRegion(
        currentUserId: Long,
        partyId: Long,
        regionCode: String,
    ): List<TripPayload> = toPayloads(currentUserId, memberTrips(currentUserId, partyId, regionCode))

    @Transactional(readOnly = true)
    fun tripStats(
        currentUserId: Long,
        partyId: Long,
    ): TripStatsPayload {
        val trips = memberTrips(currentUserId, partyId)
        return TripStatsPayload(
            tripCount = trips.size,
            regionCount = trips.map { it.regionCode }.distinct().size,
            totalTravelDays =
                trips.sumOf { (ChronoUnit.DAYS.between(it.startDate, it.endDate) + 1).toInt() },
            firstTripDate = trips.minOfOrNull { it.startDate },
            lastTripDate = trips.maxOfOrNull { it.endDate },
        )
    }

    /** 팟이 방문한 지역 목록 — 여행 이미지 상세 보기 진입 화면. */
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
        val usersById = usersById(bundle.uploaderIds)

        return trips
            .groupBy { it.regionCode }
            .entries
            // 최근에 다녀온 지역이 위로
            .sortedByDescending { (_, regionTrips) -> regionTrips.maxOf { it.startDate } }
            .map { (regionCode, regionTrips) ->
                val images = bundle.imagesOfTrips(regionTrips.ids())

                VisitedRegionPayload(
                    regionCode = regionCode,
                    visitCount = regionTrips.size,
                    images = images.take(STACK_IMAGE_LIMIT).map { it.toPayloadWith(usersById, apiBaseUrl) },
                    totalImageCount = images.size,
                    hasUnrecordedTrip = regionTrips.ids().any { it !in myTripIds },
                )
            }
    }

    /** 기록 뮤테이션 응답용 — 회차 계산에 필요한 건 같은 지역의 방문뿐이라 팟 전체를 읽지 않는다. */
    fun toPayload(
        currentUserId: Long,
        trip: Trip,
    ): TripPayload =
        assemble(
            currentUserId,
            listOf(trip),
            sameRegionTrips = tripRepository.findAllByPartyIdAndRegionCode(trip.partyId, trip.regionCode),
        ).single()

    fun requireTrip(tripId: Long): Trip =
        tripRepository.findById(tripId).orElseThrow {
            GraphQlDomainException(GraphQlErrorCode.TRIP_NOT_FOUND, "여행을 찾을 수 없습니다.")
        }

    /** 모든 조회의 첫 관문 — 멤버인지 확인하고 팟의 여행을 읽는다. [regionCode]를 주면 그 지역만. */
    private fun memberTrips(
        currentUserId: Long,
        partyId: Long,
        regionCode: String? = null,
    ): List<Trip> {
        partyQueryService.requireMember(partyId, currentUserId)
        return if (regionCode == null) {
            tripRepository.findAllByPartyId(partyId)
        } else {
            tripRepository.findAllByPartyIdAndRegionCode(partyId, regionCode)
        }
    }

    /**
     * 목록 쿼리 공통 마무리.
     * 넘어온 [trips]가 곧 회차 계산 범위라(팟 전체 또는 한 지역 전체) 추가 조회 없이 회차가 나온다.
     */
    private fun toPayloads(
        currentUserId: Long,
        trips: List<Trip>,
    ): List<TripPayload> = assemble(currentUserId, trips.sortedWith(LATEST_FIRST), sameRegionTrips = trips)

    /**
     * @param sameRegionTrips 회차("N번째 방문") 계산용 — [trips]가 속한 팟·지역의 방문을 빠짐없이 담은 목록.
     *   호출부가 이미 읽어 둔 목록을 그대로 넘기면 추가 조회가 일어나지 않는다.
     */
    private fun assemble(
        currentUserId: Long,
        trips: List<Trip>,
        sameRegionTrips: List<Trip>,
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
        val visitSequences = visitSequences(sameRegionTrips)
        // 기록을 남긴 사람과 사진 업로더는 팟을 떠났을 수 있어 현재 멤버 목록만으로는 부족하다
        val usersById =
            usersById(memberIdsByPartyId.values.flatten() + bundle.recordedMemberIds + bundle.uploaderIds)

        return trips.map { trip ->
            val tripId = requireNotNull(trip.id)
            val records =
                buildRecords(
                    currentUserId = currentUserId,
                    memberUserIds = memberIdsByPartyId.getValue(trip.partyId),
                    tripId = tripId,
                    bundle = bundle,
                    usersById = usersById,
                    apiBaseUrl = apiBaseUrl,
                )

            TripPayload(
                id = tripId,
                regionCode = trip.regionCode,
                keyword = trip.keyword,
                startDate = trip.startDate,
                endDate = trip.endDate,
                visitSequence = visitSequences[tripId] ?: 1,
                records = records,
                createdAt = requireNotNull(trip.auditMetadata.createdAt),
            )
        }
    }

    private fun usersById(userIds: Collection<Long>): Map<Long, ServiceUser> =
        userService.findAllById(userIds.distinct()).associateBy { requireNotNull(it.id) }
}

/**
 * 여행 1건의 records — 저장된 기록만이 아니라 **팟 멤버 전원**으로 채운다.
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
                comment = record?.comment,
                image = image?.toPayloadWith(usersById, apiBaseUrl),
            )
        }
}

/** 지역 카드 스택에 내려주는 대표 사진 수. 나머지는 totalImageCount로 "+N" 처리한다. */
private const val STACK_IMAGE_LIMIT = 5

/** 목록 노출 순서 — 최근에 시작한 여행이 위로. */
private val LATEST_FIRST: Comparator<Trip> =
    compareByDescending<Trip> { it.startDate }
        .thenByDescending { it.auditMetadata.createdAt ?: LocalDateTime.MIN }

/** 회차 계산 순서 — 먼저 다녀온 여행이 1회차. */
private val CHRONOLOGICAL: Comparator<Trip> =
    compareBy<Trip> { it.startDate }.thenBy { it.id ?: 0L }

/** 팟별로 지역마다 방문 순서를 매겨 "N번째 방문"을 구한다. */
private fun visitSequences(trips: List<Trip>): Map<Long, Int> =
    trips
        .groupBy { it.partyId to it.regionCode }
        .values
        .flatMap { regionTrips ->
            regionTrips
                .sortedWith(CHRONOLOGICAL)
                .mapIndexed { index, trip -> requireNotNull(trip.id) to index + 1 }
        }.toMap()

private fun List<Trip>.ids(): List<Long> = map { requireNotNull(it.id) }

private fun Image.toPayloadWith(
    usersById: Map<Long, ServiceUser>,
    apiBaseUrl: String,
) = toPayload(uploaderId?.let(usersById::get), apiBaseUrl)
