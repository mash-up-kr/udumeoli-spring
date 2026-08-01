package udumeoli.tripphoto.party.dto

import udumeoli.tripphoto.user.dto.UserPayload

data class PartyPreviewPayload(
    val name: String,
    val memberCount: Int,
    val maxMemberCount: Int,
    val members: List<UserPayload>,
)
