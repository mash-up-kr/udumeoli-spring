package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.trip.entity.TripKeyword

/** 이미 있는 핀에 내 기록을 남긴다(있으면 덮어쓴다). 지역 상세의 [내 사진 올리기] CTA. */
data class RecordTripInput(
    val tripId: Long,
    val keyword: TripKeyword,
    val image: TripImageInput,
    val comment: String? = null,
)
