package udumeoli.tripphoto.party.dto

import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.user.dto.UserPayload
import udumeoli.tripphoto.user.dto.toPayload
import udumeoli.tripphoto.user.entity.ServiceUser
import java.time.LocalDateTime

data class PartyPayload(
    val id: Long,
    val name: String,
    val inviteCode: String,
    val owner: UserPayload,
    val members: List<UserPayload>,
    val createdAt: LocalDateTime,
)

fun Party.toPayload(
    owner: ServiceUser,
    members: List<ServiceUser>,
): PartyPayload =
    PartyPayload(
        id = requireNotNull(id),
        name = partyName,
        inviteCode = inviteCode,
        owner = owner.toPayload(),
        members = members.map { it.toPayload() },
        createdAt = requireNotNull(auditMetadata.createdAt),
    )
