package udumeoli.tripphoto.trip.dto

import java.time.LocalDate

data class CreateTripInput(
    val partyId: Long,
    val regionCode: String,
    val color: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val imageIds: List<Long>,
)
