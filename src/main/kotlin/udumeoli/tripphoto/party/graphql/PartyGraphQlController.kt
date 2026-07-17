package udumeoli.tripphoto.party.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.ContextValue
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.config.CurrentUserGraphQlInterceptor
import udumeoli.tripphoto.party.dto.KickMemberInput
import udumeoli.tripphoto.party.dto.PartyPayload
import udumeoli.tripphoto.party.service.PartyService

@Controller
class PartyGraphQlController(
    private val partyService: PartyService,
) {
    @QueryMapping
    fun myParties(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
    ): List<PartyPayload> = partyService.myParties(requireCurrentUserId(currentUserId))

    @QueryMapping
    fun party(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument partyId: Long,
    ): PartyPayload = partyService.party(requireCurrentUserId(currentUserId), partyId)

    @MutationMapping
    fun createParty(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument name: String,
    ): PartyPayload = partyService.createParty(requireCurrentUserId(currentUserId), name)

    @MutationMapping
    fun joinParty(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument inviteCode: String,
    ): PartyPayload = partyService.joinParty(requireCurrentUserId(currentUserId), inviteCode)

    @MutationMapping
    fun regenerateInviteCode(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument partyId: Long,
    ): PartyPayload = partyService.regenerateInviteCode(requireCurrentUserId(currentUserId), partyId)

    @MutationMapping
    fun leaveParty(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument partyId: Long,
    ): Long = partyService.leaveParty(requireCurrentUserId(currentUserId), partyId)

    @MutationMapping
    fun deleteParty(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument partyId: Long,
    ): Long = partyService.deleteParty(requireCurrentUserId(currentUserId), partyId)

    @MutationMapping
    fun kickMember(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument input: KickMemberInput,
    ): PartyPayload =
        partyService.kickMember(
            currentUserId = requireCurrentUserId(currentUserId),
            partyId = input.partyId,
            targetUserId = input.targetUserId,
        )

    private fun requireCurrentUserId(currentUserId: Long?): Long =
        currentUserId
            ?: throw GraphQlDomainException(GraphQlErrorCode.UNAUTHENTICATED, "로그인이 필요합니다.")
}
