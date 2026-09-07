package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.image.dto.ImagePayload
import udumeoli.tripphoto.user.dto.UserPayload

/** 팟이 방문한 지역 1곳과 그 지역의 여행 요약 (GraphQL `VisitedRegion`). */
data class VisitedRegionPayload(
    val regionCode: String,
    /** "n/N명"의 N — 팟 전체 멤버 수. [slots]의 크기와 같다. */
    val memberCount: Int,
    /** "n/N명"의 n — 이 지역에 사진을 올린 멤버 수. */
    val recordedMemberCount: Int,
    /** 멤버 한 명당 한 자리. 팟 가입 순서로 고정된다. */
    val slots: List<RegionMemberSlotPayload>,
    val hasUnrecordedTrip: Boolean,
)

/** 지역 카드의 멤버 자리 하나 (GraphQL `RegionMemberSlot`). */
data class RegionMemberSlotPayload(
    val member: UserPayload,
    /** 이 멤버가 이 지역에 남긴 가장 최근 사진. 아직 안 올렸으면 null. */
    val image: ImagePayload?,
)
