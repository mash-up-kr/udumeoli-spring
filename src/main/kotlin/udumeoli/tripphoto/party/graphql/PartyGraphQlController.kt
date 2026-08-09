package udumeoli.tripphoto.party.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.auth.annotation.LoginUser
import udumeoli.tripphoto.party.dto.KickMemberInput
import udumeoli.tripphoto.party.dto.PartyPayload
import udumeoli.tripphoto.party.dto.PartyPreviewPayload
import udumeoli.tripphoto.party.service.PartyCommandService
import udumeoli.tripphoto.party.service.PartyJoinService
import udumeoli.tripphoto.party.service.PartyQueryService

@Controller
class PartyGraphQlController(
    private val partyQueryService: PartyQueryService,
    private val partyCommandService: PartyCommandService,
    private val partyJoinService: PartyJoinService,
) {
    @QueryMapping
    fun myParties(
        @LoginUser currentUserId: Long,
    ): List<PartyPayload> = partyQueryService.myParties(currentUserId)

    @QueryMapping
    fun partyDetail(
        @LoginUser currentUserId: Long,
        @Argument partyId: Long,
    ): PartyPayload = partyQueryService.party(currentUserId, partyId)

    @QueryMapping
    fun partyPreview(
        @LoginUser currentUserId: Long,
        @Argument inviteCode: String,
    ): PartyPreviewPayload = partyJoinService.partyPreview(currentUserId, inviteCode)

    @MutationMapping
    fun createParty(
        @LoginUser currentUserId: Long,
        @Argument name: String,
    ): PartyPayload = partyCommandService.createParty(currentUserId, name)

    @MutationMapping
    fun joinParty(
        @LoginUser currentUserId: Long,
        @Argument inviteCode: String,
    ): PartyPayload = partyJoinService.joinParty(currentUserId, inviteCode)

    @MutationMapping
    fun regenerateInviteCode(
        @LoginUser currentUserId: Long,
        @Argument partyId: Long,
    ): PartyPayload = partyCommandService.regenerateInviteCode(currentUserId, partyId)

    @MutationMapping
    fun leaveParty(
        @LoginUser currentUserId: Long,
        @Argument partyId: Long,
    ): Long = partyCommandService.leaveParty(currentUserId, partyId)

    @MutationMapping
    fun deleteParty(
        @LoginUser currentUserId: Long,
        @Argument partyId: Long,
    ): Long = partyCommandService.deleteParty(currentUserId, partyId)

    @MutationMapping
    fun kickMember(
        @LoginUser currentUserId: Long,
        @Argument input: KickMemberInput,
    ): PartyPayload =
        partyCommandService.kickMember(
            currentUserId = currentUserId,
            partyId = input.partyId,
            targetUserId = input.targetUserId,
        )
}
