package udumeoli.tripphoto.trip.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.party.service.PartyQueryService
import udumeoli.tripphoto.trip.dto.PartyMapOverviewPayload
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository

/**
 * 지도 조회 — 줌 0~3단계 집계를 한 번에 만든다.
 *
 * 여행 목록·지역 카드·통계를 맡는 [TripQueryService]와 축이 달라(사진은 안 읽고 멤버 목록은 읽는다) 따로 둔다.
 * 집계 규칙 자체는 Spring을 모르는 [aggregate]에 있고, 여기서는 권한 확인과 조회만 한다.
 * 핀 건수와 무관하게 쿼리 5회 상수다 — requireMember 2 + 핀 1 + 기록 1 + 멤버 목록 1.
 *
 * recordedMemberCount(n)는 trip_record에 남은 기록으로 세지만, 강퇴는 party_member만 지우고
 * trip_record는 남긴다(사진 보존 정책). 그래서 n의 분자를 현재 멤버 집합과 교집합해야
 * memberCount(N)를 넘지 않는다 — [aggregate]가 [currentMemberIds]로 그 교집합을 계산한다.
 */
@Service
class PartyMapQueryService(
    private val tripRepository: TripRepository,
    private val tripRecordRepository: TripRecordRepository,
    private val partyQueryService: PartyQueryService,
) {
    @Transactional(readOnly = true)
    fun mapOverview(
        currentUserId: Long,
        partyId: Long,
    ): PartyMapOverviewPayload {
        partyQueryService.requireMember(partyId, currentUserId)

        val allTrips = tripRepository.findAllByPartyId(partyId)
        val currentMemberIds = partyQueryService.memberUserIdsInJoinOrder(partyId).toSet()
        // 핀이 없으면 기록도 없다 — 빈 IN 절로 DB를 한 번 더 왕복하지 않는다.
        val recordsByTripId =
            if (allTrips.isEmpty()) {
                emptyMap()
            } else {
                tripRecordRepository
                    .findAllByTripIdIn(allTrips.map { requireNotNull(it.id) })
                    .groupBy { it.tripId }
            }

        // 대표 스티커를 기록에서 뽑으므로 기록이 하나도 없는 핀은 그릴 값이 없다.
        // 마지막 기록이 지워지면 핀도 함께 지워지도록 되어 있어 정상 흐름에서는 생기지 않지만,
        // 남아 있더라도 지도가 터지지 않도록 여기서 걸러 낸다.
        val trips = allTrips.filter { !recordsByTripId[requireNotNull(it.id)].isNullOrEmpty() }

        return aggregate(trips, recordsByTripId, currentMemberIds, currentUserId)
    }
}
