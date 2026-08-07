package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.trip.entity.TripKeyword
import java.time.LocalDate

/** 새 방문을 만들면서 내 기록까지 함께 남긴다. (기록하기 플로우) */
data class CreateTripInput(
    val partyId: Long,
    val regionCode: String,
    val keyword: TripKeyword,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val image: TripImageInput,
    val comment: String? = null,
)
