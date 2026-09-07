package udumeoli.tripphoto.trip.service

import udumeoli.tripphoto.trip.dto.MapCellPayload
import udumeoli.tripphoto.trip.dto.PartyMapOverviewPayload
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripKeyword
import udumeoli.tripphoto.trip.entity.TripRecord

/**
 * 줌 레벨별 지도 집계 규칙 — Spring 빈이 아니고 DB를 모른다.
 *
 * 레벨이 달라도 하는 일은 "묶고 대표값을 뽑는" 하나뿐이라, 바뀌는 건 그룹 키뿐이다.
 * 행정구역 코드가 이미 접두사 계층이라(32030 → 32) take(2) 한 줄로 시·군 → 시·도 롤업이 된다.
 * 서울 같은 2자리 코드는 잘라도 그대로라 예외 처리가 없다.
 * memberCount는 [currentMemberIds]의 크기에서 그대로 파생된다 — "n/N"의 N과 n의 모수가 항상 같은 집합이다.
 *
 * 키워드는 핀이 아니라 [recordsByTripId]의 기록마다 붙는다. 같은 지역이라도 올린 사람마다 다른 키워드를
 * 고를 수 있어서, 대표 스티커는 핀이 아니라 기록을 세어 뽑는다.
 *
 * [currentUserId]는 칸을 회색으로 칠할지 가르는 데만 쓴다. 지도는 "내가 이 칸을 다 기록했는가"로
 * 스티커 노출을 결정하므로, 남이 몇 명 기록했는지(recordedMemberCount)와는 축이 다른 값이 하나 더 필요하다.
 */
internal fun aggregate(
    trips: List<Trip>,
    recordsByTripId: Map<Long, List<TripRecord>>,
    currentMemberIds: Set<Long>,
    currentUserId: Long,
): PartyMapOverviewPayload =
    PartyMapOverviewPayload(
        memberCount = currentMemberIds.size,
        country = cells(trips, recordsByTripId, currentMemberIds, currentUserId) { COUNTRY_CODE }.singleOrNull(),
        provinces = cells(trips, recordsByTripId, currentMemberIds, currentUserId) { it.take(2) },
        municipalities = cells(trips, recordsByTripId, currentMemberIds, currentUserId) { it },
    )

/** 0단계 칸의 코드. 프론트는 전국 폴리곤 하나에 이 코드를 매칭한다. */
private const val COUNTRY_CODE = "KR"

/**
 * [groupKey]로 핀을 묶어 칸을 만든다. 핀이 없으면 빈 목록이라 country가 자연히 null이 된다.
 * 순서는 regionCode 오름차순 — 프론트는 Map으로 인덱싱해 순서에 의존하지 않지만, 응답을 결정론적으로 두려는 것이다.
 */
private fun cells(
    trips: List<Trip>,
    recordsByTripId: Map<Long, List<TripRecord>>,
    currentMemberIds: Set<Long>,
    currentUserId: Long,
    groupKey: (String) -> String,
): List<MapCellPayload> =
    trips
        .groupBy { groupKey(it.regionCode) }
        .map { (regionCode, cellTrips) ->
            val cellRecords = cellTrips.flatMap { recordsByTripId[requireNotNull(it.id)].orEmpty() }

            MapCellPayload(
                regionCode = regionCode,
                keyword = representativeKeyword(cellRecords),
                // 지역마다 핀이 하나뿐이라(uq_trip_party_region) 핀 수가 곧 지역 수다.
                regionCount = cellTrips.size,
                // 탈퇴/강퇴된 멤버의 기록은 trip_record에 남아 있어도 현재 멤버가 아니면 세지 않는다.
                recordedMemberCount =
                    cellRecords
                        .map { it.serviceUserId }
                        .toSet()
                        .intersect(currentMemberIds)
                        .size,
                // 이 칸에 내가 아직 안 올린 핀이 하나라도 있으면 회색이다.
                hasUnrecordedTrip =
                    cellTrips.any { trip ->
                        recordsByTripId[requireNotNull(trip.id)].orEmpty().none { it.serviceUserId == currentUserId }
                    },
                latestTripAt = cellTrips.maxOf { requireNotNull(it.auditMetadata.createdAt) },
            )
        }.sortedBy { it.regionCode }

/**
 * 최빈 → 동률이면 이름 가나다순으로 가장 앞선 키워드.
 *
 * 기록이 아니라 키워드를 줄 세운다 — 후보가 키워드 집합이라 같은 키워드가 두 번 겹칠 일이 없고,
 * 그래서 두 기준만으로 순위가 완전히 갈린다. 결정론을 위한 id 같은 최후 기준이 따로 필요 없다.
 */
private fun representativeKeyword(records: List<TripRecord>): TripKeyword {
    val countByKeyword = records.groupingBy { it.keyword }.eachCount()
    return countByKeyword.keys.minWith(
        compareByDescending<TripKeyword> { countByKeyword.getValue(it) }
            .thenBy { it.koreanName },
    )
}
