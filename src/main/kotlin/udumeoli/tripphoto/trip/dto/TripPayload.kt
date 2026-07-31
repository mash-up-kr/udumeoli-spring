package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.image.dto.ImagePayload
import udumeoli.tripphoto.region.dto.RegionPayload
import udumeoli.tripphoto.user.dto.UserPayload
import java.time.LocalDate
import java.time.LocalDateTime

data class TripPayload(
    val id: Long,
    val region: RegionPayload,
    val color: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val images: List<ImagePayload>,
    val createdBy: UserPayload?,
    val createdAt: LocalDateTime,
)
