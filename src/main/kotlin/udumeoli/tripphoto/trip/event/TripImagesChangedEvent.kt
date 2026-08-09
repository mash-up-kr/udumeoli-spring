package udumeoli.tripphoto.trip.event

import udumeoli.tripphoto.image.entity.Image

/**
 * - attachedImages: 새로 연결된 사진 → 썸네일 생성 요청 대상
 * - detachedImageIds: 연결이 끊긴 사진 → 삭제 대상
 */
data class TripImagesChangedEvent(
    val attachedImages: List<Image> = emptyList(),
    val detachedImageIds: List<Long> = emptyList(),
)
