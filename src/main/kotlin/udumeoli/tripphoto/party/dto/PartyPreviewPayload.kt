package udumeoli.tripphoto.party.dto

import udumeoli.tripphoto.user.dto.UserPayload

/** 참여 확정 전 확인 모달용 팟 요약 (GraphQL `PartyPreview`). */
data class PartyPreviewPayload(
    val name: String,
    val memberCount: Int,
    val members: List<UserPayload>,
)
