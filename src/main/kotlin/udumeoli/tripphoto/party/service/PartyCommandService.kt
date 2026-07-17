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
class PartyCommandService(
    private val partyRepository: PartyRepository,
    private val partyMemberRepository: PartyMemberRepository,
    private val userService: UserService,
    private val inviteCodeIssuer: InviteCodeIssuer,
    private val partyQueryService: PartyQueryService,
) {
    @Transactional
    fun createParty(
        currentUserId: Long,
        name: String,
    ): PartyPayload {
        userService.currentUser(currentUserId)
        validateNonEmpty(name, "여행팟 이름을 입력해주세요.")

        val party =
            inviteCodeIssuer.saveWithUniqueInviteCode { inviteCode ->
                partyRepository.save(
                    Party(
                        partyName = name,
                        inviteCode = inviteCode,
                        ownerId = currentUserId,
                    ),
                )
            }

        partyMemberRepository.save(
            PartyMember(
                partyId = requireNotNull(party.id),
                serviceUserId = currentUserId,
            ),
        )

        return partyQueryService.toPayload(party)
    }

    @Transactional
    fun leaveParty(
        currentUserId: Long,
        partyId: Long,
    ): Long {
        val party =
            partyRepository.findById(partyId).orElseThrow {
                GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
            }
        partyQueryService.requireMember(partyId, currentUserId)

        if (!party.canLeave(currentUserId)) {
            throw GraphQlDomainException(
                GraphQlErrorCode.OWNER_CANNOT_LEAVE,
                "방장은 여행팟을 나갈 수 없습니다.",
            )
        }

        val member =
            partyMemberRepository.findByPartyIdAndServiceUserId(partyId, currentUserId)
                ?: throw GraphQlDomainException(GraphQlErrorCode.MEMBER_NOT_FOUND, "멤버를 찾을 수 없습니다.")
        partyMemberRepository.delete(member)
        return partyId
    }

    @Transactional
    fun deleteParty(
        currentUserId: Long,
        partyId: Long,
    ): Long {
        val party =
            partyRepository.findById(partyId).orElseThrow {
                GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
            }
        requireOwner(party, currentUserId)

        val members = partyMemberRepository.findAllByPartyId(partyId)
        if (members.any { it.serviceUserId != currentUserId }) {
            throw GraphQlDomainException(
                GraphQlErrorCode.PARTY_HAS_MEMBERS,
                "다른 멤버가 남아 있어 여행팟을 삭제할 수 없습니다.",
            )
        }

        partyMemberRepository.deleteAll(members)
        partyRepository.delete(party)
        return partyId
    }

    @Transactional
    fun kickMember(
        currentUserId: Long,
        partyId: Long,
        targetUserId: Long,
    ): PartyPayload {
        val party =
            partyRepository.findById(partyId).orElseThrow {
                GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
            }
        requireOwner(party, currentUserId)

        if (!party.canKick(targetUserId)) {
            throw GraphQlDomainException(
                GraphQlErrorCode.CANNOT_REMOVE_OWNER,
                "방장은 강퇴할 수 없습니다.",
            )
        }

        val member =
            partyMemberRepository.findByPartyIdAndServiceUserId(partyId, targetUserId)
                ?: throw GraphQlDomainException(GraphQlErrorCode.MEMBER_NOT_FOUND, "멤버를 찾을 수 없습니다.")
        partyMemberRepository.delete(member)
        return partyQueryService.toPayload(party)
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

    private fun validateNonEmpty(
        value: String,
        message: String,
    ) {
        if (value.isEmpty()) {
            throw GraphQlDomainException(GraphQlErrorCode.VALIDATION_ERROR, message)
        }
    }
}
