package udumeoli.tripphoto.trip.dto

import java.time.LocalDate

/** 팟의 여행 전체를 아우르는 집계 (GraphQL `TripStats`). */
data class TripStatsPayload(
    val tripCount: Int,
    val regionCount: Int,
    val totalTravelDays: Int,
    val firstTripDate: LocalDate?,
    val lastTripDate: LocalDate?,
)
