package udumeoli.tripphoto.party.event

import java.time.Instant

data class CampaignFanJoined(
    val templatePartyId: Long,
    val partyId: Long,
    val partyName: String,
    val fanUserId: Long,
    val fanNickname: String,
    val joinedAt: Instant = Instant.now(),
)
