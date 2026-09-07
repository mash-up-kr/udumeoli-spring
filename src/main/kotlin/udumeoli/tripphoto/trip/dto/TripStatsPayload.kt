package udumeoli.tripphoto.trip.dto

/** 리캡 카드용 집계 (GraphQL `TripStats`). */
data class TripStatsPayload(
    /** "{국가명}에서 {N}개의 핀을 만들었어요"의 N. 핀은 지역마다 하나라 곧 방문한 지역 수다. */
    val regionCount: Int,
)
