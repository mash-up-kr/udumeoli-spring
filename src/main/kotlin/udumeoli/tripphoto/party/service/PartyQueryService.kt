package udumeoli.tripphoto.party.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.party.dto.PartyPayload
import udumeoli.tripphoto.party.dto.toPayload
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.user.service.UserService

@Service
class PartyQueryService(
    private val partyRepository: PartyRepository,
    private val partyMemberRepository: PartyMemberRepository,
    private val userService: UserService,
) {
    @Transactional(readOnly = true)
    fun myParties(currentUserId: Long): List<PartyPayload> {
        userService.currentUser(currentUserId)
        val partyIds = partyMemberRepository.findAllByServiceUserId(currentUserId).map { it.partyId }
        if (partyIds.isEmpty()) {
            return emptyList()
        }

        val partiesById = partyRepository.findAllById(partyIds).associateBy { requireNotNull(it.id) }
        return partyIds.mapNotNull { partiesById[it] }.map { toPayload(it) }
    }

    @Transactional(readOnly = true)
    fun party(
        currentUserId: Long,
        partyId: Long,
    ): PartyPayload {
        val party =
            partyRepository.findById(partyId).orElseThrow {
                GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
            }
        requireMember(partyId, currentUserId)
        return toPayload(party)
    }

    fun toPayload(party: Party): PartyPayload {
        val partyId = requireNotNull(party.id)
        val memberUserIds = memberUserIds(partyId)
        val usersById =
            userService
                .findAllById((memberUserIds + party.ownerId).distinct())
                .associateBy { requireNotNull(it.id) }
        val owner =
            usersById[party.ownerId]
                ?: throw GraphQlDomainException(GraphQlErrorCode.UNAUTHENTICATED, "방장 정보를 찾을 수 없습니다.")

        return party.toPayload(
            owner = owner,
            members = memberUserIds.mapNotNull { usersById[it] },
        )
    }

    fun requireMember(
        partyId: Long,
        userId: Long,
    ) {
        userService.currentUser(userId)
        if (!partyMemberRepository.existsByPartyIdAndServiceUserId(partyId, userId)) {
            throw GraphQlDomainException(GraphQlErrorCode.FORBIDDEN, "여행팟 멤버만 접근할 수 있습니다.")
        }
    }

    private fun memberUserIds(partyId: Long): List<Long> =
        partyMemberRepository
            .findAllByPartyId(partyId)
            .sortedWith(compareBy<PartyMember> { it.serviceUserId }.thenBy { it.id ?: Long.MAX_VALUE })
            .map { it.serviceUserId }
}
