package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.image.dto.ImagePayload

/** 여행 이미지 상세 보기 진입 화면의 지역 카드 1장. */
data class RegionCardPayload(
    val regionCode: Int,
    val visitCount: Int,
    val images: List<ImagePayload>,
    val totalImageCount: Int,
    val hasUnrecordedTrip: Boolean,
)
