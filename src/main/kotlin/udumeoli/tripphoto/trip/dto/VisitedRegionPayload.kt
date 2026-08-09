package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.image.dto.ImagePayload

/** 팟이 방문한 지역 1곳과 그 지역의 여행 요약 (GraphQL `VisitedRegion`). */
data class VisitedRegionPayload(
    val regionCode: String,
    val visitCount: Int,
    val images: List<ImagePayload>,
    val totalImageCount: Int,
    val hasUnrecordedTrip: Boolean,
)
