package udumeoli.tripphoto.party.event

import java.time.Instant

data class PartyCopied(
    val sourcePartyId: Long,
    val copiedPartyId: Long,
    val partyName: String,
    val copiedByUserId: Long,
    val copiedByNickname: String,
    val copiedAt: Instant = Instant.now(),
)
