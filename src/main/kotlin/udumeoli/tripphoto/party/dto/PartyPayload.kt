package udumeoli.tripphoto.party.dto

import udumeoli.tripphoto.user.dto.UserPayload
import java.time.LocalDateTime

data class PartyPayload(
    val id: Long,
    val name: String,
    val inviteCode: String,
    val owner: UserPayload,
    val members: List<UserPayload>,
    val createdAt: LocalDateTime,
)
