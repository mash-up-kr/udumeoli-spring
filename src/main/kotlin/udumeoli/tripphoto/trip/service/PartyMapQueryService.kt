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
 * 여행 건수와 무관하게 쿼리 5회 상수다 — requireMember 2 + 여행 1 + 기록 1 + 멤버 목록 1.
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

        val trips = tripRepository.findAllByPartyId(partyId)
        val currentMemberIds = partyQueryService.memberUserIdsInJoinOrder(partyId).toSet()
        // 여행이 없으면 기록도 없다 — 빈 IN 절로 DB를 한 번 더 왕복하지 않는다.
        val memberIdsByTripId =
            if (trips.isEmpty()) {
                emptyMap()
            } else {
                tripRecordRepository
                    .findAllByTripIdIn(trips.map { requireNotNull(it.id) })
                    .groupBy { it.tripId }
                    .mapValues { (_, records) -> records.map { it.serviceUserId }.toSet() }
            }

        return aggregate(trips, memberIdsByTripId, currentMemberIds, currentUserId)
    }
}
