package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.image.dto.ImagePayload
import udumeoli.tripphoto.region.dto.RegionPayload

/** 여행 이미지 상세 보기 진입 화면의 지역 카드 1장. */
data class RegionCardPayload(
    val region: RegionPayload,
    val visitCount: Int,
    val images: List<ImagePayload>,
    val totalImageCount: Int,
    val hasUnrecordedTrip: Boolean,
)
