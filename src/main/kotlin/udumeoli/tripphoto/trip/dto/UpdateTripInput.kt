package udumeoli.tripphoto.trip.dto

import java.time.LocalDate

data class UpdateTripInput(
    val tripId: Long,
    val regionCode: String? = null,
    val color: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val imageIds: List<Long>? = null,
)
