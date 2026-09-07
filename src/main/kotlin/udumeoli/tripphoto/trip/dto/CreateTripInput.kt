package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.trip.entity.TripKeyword

/** 지역에 내 기록을 남긴다 — 그 지역 핀이 아직 없으면 함께 만든다. (기록하기 플로우) */
data class CreateTripInput(
    val partyId: Long,
    val regionCode: String,
    val keyword: TripKeyword,
    val image: TripImageInput,
    val comment: String? = null,
)
