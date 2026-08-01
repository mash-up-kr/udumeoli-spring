package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.image.dto.ImagePayload
import udumeoli.tripphoto.user.dto.UserPayload

/**
 * 여행 기록의 한 행. 팟 멤버 전원이 한 행씩 내려가며,
 * 아직 사진을 올리지 않은 멤버는 [recorded] = false 인 placeholder 행이 된다.
 * "내 기록"은 별도 플래그 없이 목록 맨 앞에 오는 것으로 표현한다.
 */
data class TripRecordPayload(
    val member: UserPayload,
    val recorded: Boolean,
    val comment: String?,
    val images: List<ImagePayload>,
)
