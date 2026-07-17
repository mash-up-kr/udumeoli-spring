package udumeoli.tripphoto.party.service

import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.party.dto.PartyPayload
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.user.dto.toPayload
import udumeoli.tripphoto.user.service.UserService
import java.security.SecureRandom

@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val partyMemberRepository: PartyMemberRepository,
    private val userService: UserService,
    private val joinPartyRateLimiter: JoinPartyRateLimiter,
) {
    @Transactional(readOnly = true)
    fun myParties(currentUserId: Long): List<PartyPayload> {
        userService.currentUser(currentUserId)
        val partyIds = partyMemberRepository.findAllByServiceUserId(currentUserId).map { it.partyId }
        if (partyIds.isEmpty()) {
            return emptyList()
        }

        val partiesById = partyRepository.findAllById(partyIds).associateBy { requireNotNull(it.id) }
        return partyIds.mapNotNull { partiesById[it] }.map { it.toPayload() }
    }

    @Transactional(readOnly = true)
    fun party(
        currentUserId: Long,
        partyId: Long,
    ): PartyPayload {
        val party = findParty(partyId)
        requireMember(partyId, currentUserId)
        return party.toPayload()
    }

    @Transactional
    fun createParty(
        currentUserId: Long,
        name: String,
    ): PartyPayload {
        userService.currentUser(currentUserId)
        validateNonEmpty(name, "여행팟 이름을 입력해주세요.")

        val party =
            saveWithUniqueInviteCode { inviteCode ->
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

        return party.toPayload()
    }

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

        if (partyMemberRepository.existsByPartyIdAndServiceUserId(partyId, currentUserId)) {
            throw GraphQlDomainException(
                GraphQlErrorCode.ALREADY_JOINED_PARTY,
                "이미 참여 중인 여행팟입니다.",
            )
        }

        if (partyMemberRepository.countByPartyId(partyId) >= MAX_PARTY_MEMBERS) {
            throw GraphQlDomainException(
                GraphQlErrorCode.PARTY_FULL,
                "정원이 다 찼어요. ($MAX_PARTY_MEMBERS/$MAX_PARTY_MEMBERS)",
            )
        }

        partyMemberRepository.save(PartyMember(partyId = partyId, serviceUserId = currentUserId))
        return party.toPayload()
    }

    @Transactional
    fun regenerateInviteCode(
        currentUserId: Long,
        partyId: Long,
    ): PartyPayload {
        val party = findParty(partyId)
        requireOwner(party, currentUserId)

        return saveWithUniqueInviteCode { inviteCode ->
            partyRepository.save(party.copy(inviteCode = inviteCode))
        }.toPayload()
    }

    @Transactional
    fun leaveParty(
        currentUserId: Long,
        partyId: Long,
    ): Long {
        val party = findParty(partyId)
        requireMember(partyId, currentUserId)

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
        val party = findParty(partyId)
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
        val party = findParty(partyId)
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
        return party.toPayload()
    }

    private fun findParty(partyId: Long): Party =
        partyRepository.findById(partyId).orElseThrow {
            GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
        }

    private fun requireMember(
        partyId: Long,
        userId: Long,
    ) {
        userService.currentUser(userId)
        if (!partyMemberRepository.existsByPartyIdAndServiceUserId(partyId, userId)) {
            throw GraphQlDomainException(GraphQlErrorCode.FORBIDDEN, "여행팟 멤버만 접근할 수 있습니다.")
        }
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

    private fun Party.toPayload(): PartyPayload {
        val partyId = requireNotNull(id)
        val memberUserIds = memberUserIds(partyId)
        val usersById = userService.findAllById((memberUserIds + ownerId).distinct()).associateBy { requireNotNull(it.id) }
        val owner =
            usersById[ownerId]
                ?: throw GraphQlDomainException(GraphQlErrorCode.UNAUTHENTICATED, "방장 정보를 찾을 수 없습니다.")

        return PartyPayload(
            id = partyId,
            name = partyName,
            inviteCode = inviteCode,
            owner = owner.toPayload(),
            members = memberUserIds.mapNotNull { usersById[it]?.toPayload() },
            createdAt = requireNotNull(auditMetadata.createdAt),
        )
    }

    private fun memberUserIds(partyId: Long): List<Long> =
        partyMemberRepository
            .findAllByPartyId(partyId)
            .sortedWith(compareBy<PartyMember> { it.serviceUserId }.thenBy { it.id ?: Long.MAX_VALUE })
            .map { it.serviceUserId }

    private fun validateNonEmpty(
        value: String,
        message: String,
    ) {
        if (value.isEmpty()) {
            throw GraphQlDomainException(GraphQlErrorCode.VALIDATION_ERROR, message)
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

    private fun saveWithUniqueInviteCode(save: (String) -> Party): Party {
        repeat(INVITE_CODE_GENERATION_ATTEMPTS) {
            try {
                return save(generateInviteCode())
            } catch (e: DuplicateKeyException) {
                if (it == INVITE_CODE_GENERATION_ATTEMPTS - 1) {
                    throw e
                }
            }
        }
        error("초대코드 생성에 실패했습니다.")
    }

    private fun generateInviteCode(): String {
        while (true) {
            val code =
                (1..INVITE_CODE_LENGTH)
                    .map { INVITE_CODE_ALPHABET[random.nextInt(INVITE_CODE_ALPHABET.length)] }
                    .joinToString("")
            if (code.any { it.isLetter() } && code.any { it.isDigit() }) {
                return code
            }
        }
    }

    companion object {
        const val MAX_PARTY_MEMBERS = 6L
        private const val INVITE_CODE_LENGTH = 6
        private const val INVITE_CODE_GENERATION_ATTEMPTS = 10
        private const val INVITE_CODE_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"
        private val random = SecureRandom()
    }
}
