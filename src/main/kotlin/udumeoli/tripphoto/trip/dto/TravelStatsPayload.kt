package udumeoli.tripphoto.trip.dto

import java.time.LocalDate

data class TravelStatsPayload(
    val tripCount: Int,
    val regionCount: Int,
    val totalTravelDays: Int,
    val firstTripDate: LocalDate?,
    val lastTripDate: LocalDate?,
)
