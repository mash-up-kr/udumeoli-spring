package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.trip.entity.TripKeyword
import java.time.LocalDateTime

/**
 * 지도 한 화면 분의 집계 (GraphQL `PartyMapOverview`).
 * 폴리곤 좌표는 담지 않는다 — 프론트 GeoJSON의 properties.code와 대조할 조회표다.
 */
data class PartyMapOverviewPayload(
    /** "n/N" 배지의 분모. 칸마다 같은 값이라 최상단에 한 번만 둔다. */
    val memberCount: Int,
    /** 0단계 — 전국을 하나로 묶은 칸. 여행이 하나도 없으면 null. */
    val country: MapCellPayload?,
    /** 1단계 — 시·도 단위. 방문한 시·도만 담긴다. */
    val provinces: List<MapCellPayload>,
    /** 2·3단계 — 시·군·구 단위. 방문한 지역만 담긴다. */
    val municipalities: List<MapCellPayload>,
)

/** 지도에서 색칠되는 구역 하나 (GraphQL `MapCell`). */
data class MapCellPayload(
    /** country는 "KR" 고정, provinces는 2자리, municipalities는 trip에 기록된 값 그대로다. */
    val regionCode: String,
    /** 대표 키워드 스티커. */
    val keyword: TripKeyword,
    /** "+N" 배지 — 이 칸에서 다녀온 서로 다른 시·군·구 수. 재방문은 1로 센다. */
    val regionCount: Int,
    /** 이 칸 안의 총 방문 횟수. 재방문도 각각 센다. */
    val visitCount: Int,
    /** "n/N"의 n — 이 칸에 기록을 남긴 서로 다른 "현재" 멤버 수. 탈퇴/강퇴된 멤버의 기록은 세지 않는다. */
    val recordedMemberCount: Int,
    /** 이 칸에 내가 아직 기록하지 않은 여행이 하나라도 있으면 true. 회색 처리 + "탭해서 기록하기"의 조건이다. */
    val hasUnrecordedTrip: Boolean,
    /** 이 칸에서 가장 나중에 등록된 여행의 등록 시각. "가장 최근 지역 1곳에만 툴팁"을 프론트가 고르는 기준이다. */
    val latestTripAt: LocalDateTime,
)
