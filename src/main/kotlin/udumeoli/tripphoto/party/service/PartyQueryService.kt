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
import java.time.LocalDateTime

@Service
class PartyQueryService(
    private val partyRepository: PartyRepository,
    private val partyMemberRepository: PartyMemberRepository,
    private val userService: UserService,
) {
    @Transactional(readOnly = true)
    fun myParties(currentUserId: Long): List<PartyPayload> {
        userService.getCurrentUser(currentUserId)
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
        val party = requireParty(partyId)
        requireMember(partyId, currentUserId)
        return toPayload(party)
    }

    @Transactional(readOnly = true)
    fun isOwner(
        partyId: Long,
        userId: Long,
    ): Boolean {
        userService.getCurrentUser(userId)
        return requireParty(partyId).isOwner(userId)
    }

    fun toPayload(party: Party): PartyPayload {
        val partyId = requireNotNull(party.id)
        val memberUserIds = memberUserIdsInJoinOrder(partyId)
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
        userService.getCurrentUser(userId)
        if (!partyMemberRepository.existsByPartyIdAndServiceUserId(partyId, userId)) {
            throw GraphQlDomainException(GraphQlErrorCode.FORBIDDEN, "여행팟 멤버만 접근할 수 있습니다.")
        }
    }

    /**
     * 팟원을 가입 순서로 반환한다 (owner가 항상 첫 번째).
     */
    fun memberUserIdsInJoinOrder(partyId: Long): List<Long> =
        partyMemberRepository
            .findAllByPartyId(partyId)
            .sortedWith(JOIN_ORDER)
            .map { it.serviceUserId }

    private fun requireParty(partyId: Long): Party =
        partyRepository.findById(partyId).orElseThrow {
            GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
        }

    companion object {
        private val JOIN_ORDER: Comparator<PartyMember> =
            compareBy<PartyMember> { it.createdAt ?: LocalDateTime.MIN }
                .thenBy { it.id ?: Long.MAX_VALUE }
    }
}
