package udumeoli.tripphoto.trip.dto

import java.time.LocalDate

data class TripImageInput(
    val imageId: Long,
    val takenAt: LocalDate? = null,
)
