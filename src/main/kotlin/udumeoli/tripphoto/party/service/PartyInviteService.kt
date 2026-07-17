package udumeoli.tripphoto.party.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.party.dto.PartyPayload
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.user.service.UserService

@Service
class PartyInviteService(
    private val partyRepository: PartyRepository,
    private val partyMemberRepository: PartyMemberRepository,
    private val userService: UserService,
    private val joinPartyRateLimiter: JoinPartyRateLimiter,
    private val inviteCodeIssuer: InviteCodeIssuer,
    private val partyQueryService: PartyQueryService,
) {
    @Transactional
    fun joinParty(
        currentUserId: Long,
        inviteCode: String,
    ): PartyPayload {
        userService.currentUser(currentUserId)
        joinPartyRateLimiter.check(currentUserId)
        validateInviteCode(inviteCode)

        val party =
            partyRepository.findByInviteCodeForUpdate(inviteCode)
                ?: throw GraphQlDomainException(
                    GraphQlErrorCode.INVALID_INVITE_CODE,
                    "존재하지 않는 초대코드입니다.",
                )
        val partyId = requireNotNull(party.id)

        requireNotAlreadyJoined(
            partyMemberRepository.existsByPartyIdAndServiceUserId(partyId, currentUserId),
        )

        requirePartyCapacity(partyMemberRepository.countByPartyId(partyId))

        partyMemberRepository.save(PartyMember(partyId = partyId, serviceUserId = currentUserId))
        return partyQueryService.toPayload(party)
    }

    @Transactional
    fun regenerateInviteCode(
        currentUserId: Long,
        partyId: Long,
    ): PartyPayload {
        val party =
            partyRepository.findById(partyId).orElseThrow {
                GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
            }
        requireOwner(party, currentUserId)

        return inviteCodeIssuer
            .saveWithUniqueInviteCode { inviteCode ->
                partyRepository.save(party.copy(inviteCode = inviteCode))
            }.let { partyQueryService.toPayload(it) }
    }

    private fun requireOwner(
        party: Party,
        userId: Long,
    ) {
        userService.currentUser(userId)
        if (!party.isOwner(userId)) {
            throw GraphQlDomainException(GraphQlErrorCode.FORBIDDEN, "방장만 수행할 수 있습니다.")
        }
    }

    private fun validateInviteCode(inviteCode: String) {
        if (inviteCode.length != INVITE_CODE_LENGTH || inviteCode.any { !it.isLetterOrDigit() }) {
            throw GraphQlDomainException(
                GraphQlErrorCode.INVALID_INVITE_CODE,
                "초대코드는 영문과 숫자 6자리여야 합니다.",
            )
        }
    }

    private fun requireNotAlreadyJoined(alreadyJoined: Boolean) {
        if (alreadyJoined) {
            throw GraphQlDomainException(
                GraphQlErrorCode.ALREADY_JOINED_PARTY,
                "이미 참여 중인 여행팟입니다.",
            )
        }
    }

    private fun requirePartyCapacity(memberCount: Long) {
        if (memberCount >= MAX_PARTY_MEMBERS) {
            throw GraphQlDomainException(
                GraphQlErrorCode.PARTY_FULL,
                "정원이 다 찼어요. ($MAX_PARTY_MEMBERS/$MAX_PARTY_MEMBERS)",
            )
        }
    }

    companion object {
        const val MAX_PARTY_MEMBERS = 6L
        private const val INVITE_CODE_LENGTH = 6
    }
}
