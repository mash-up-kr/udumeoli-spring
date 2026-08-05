package udumeoli.tripphoto.party.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.party.dto.PartyPayload
import udumeoli.tripphoto.party.dto.PartyPreviewPayload
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.user.dto.toPayload
import udumeoli.tripphoto.user.service.UserService

/**
 * 초대코드로 팟에 들어오는 흐름 — 참여 전 미리보기(partyPreview)와 참여(joinParty).
 * 코드 발급/재발급은 owner 액션이라 [PartyCommandService]가 맡는다.
 */
@Service
class PartyJoinService(
    private val partyRepository: PartyRepository,
    private val partyMemberRepository: PartyMemberRepository,
    private val userService: UserService,
    private val joinPartyRateLimiter: JoinPartyRateLimiter,
    private val partyQueryService: PartyQueryService,
) {
    @Transactional
    fun joinParty(
        currentUserId: Long,
        inviteCode: String,
    ): PartyPayload {
        // 정원 초과 동시 참여를 막으려면 검사부터 저장까지 팟 행을 잠근 채로 진행해야 한다
        val party = resolveJoinableParty(currentUserId, inviteCode, partyRepository::findByInviteCodeForUpdate)
        val partyId = requireNotNull(party.id)

        requirePartyCapacity(partyMemberRepository.countByPartyId(partyId))

        partyMemberRepository.save(PartyMember(partyId = partyId, serviceUserId = currentUserId))
        return partyQueryService.toPayload(party)
    }

    @Transactional(readOnly = true)
    fun partyPreview(
        currentUserId: Long,
        inviteCode: String,
    ): PartyPreviewPayload {
        val party = resolveJoinableParty(currentUserId, inviteCode, partyRepository::findByInviteCode)

        val memberUserIds = partyQueryService.memberUserIdsInJoinOrder(requireNotNull(party.id))
        requirePartyCapacity(memberUserIds.size.toLong())

        val usersById = userService.findAllById(memberUserIds).associateBy { requireNotNull(it.id) }
        return PartyPreviewPayload(
            name = party.partyName,
            memberCount = memberUserIds.size,
            members = memberUserIds.mapNotNull { usersById[it] }.map { it.toPayload() },
        )
    }

    /**
     * [joinParty]와 [partyPreview]가 함께 쓰는 검문소. 아래 순서로 걸러 통과한 팟만 돌려준다.
     *
     * 1. 실제 존재하는 사용자인가
     * 2. 요청이 너무 잦지 않은가 — 초대코드 무차별 대입 차단
     * 3. 코드가 영문·숫자 6자리인가
     * 4. 그 코드의 팟이 있는가
     * 5. 이미 참여 중인 팟은 아닌가
     *
     * 형식 검사(3)를 조회(4)보다 먼저 하는 건, 형식부터 틀린 코드로는 DB를 건드리지 않기 위해서다.
     *
     * 정원 검사는 일부러 뺐다. 두 흐름이 인원수를 구하는 방법이 달라서다 —
     * 참여는 count 쿼리로 세고, 미리보기는 화면에 뿌리려고 이미 읽어 둔 멤버 목록의 크기를 쓴다.
     *
     * @param findByInviteCode 팟을 찾아오는 방법.
     *   참여는 정원을 초과한 동시 참여를 막아야 해서 조회하면서 팟 행을 잠그고(FOR UPDATE),
     *   읽기만 하는 미리보기는 잠그지 않는다.
     */
    private fun resolveJoinableParty(
        currentUserId: Long,
        inviteCode: String,
        findByInviteCode: (String) -> Party?,
    ): Party {
        userService.getCurrentUser(currentUserId)
        joinPartyRateLimiter.check(currentUserId)
        validateInviteCode(inviteCode)

        val party =
            findByInviteCode(inviteCode)
                ?: throw GraphQlDomainException(
                    GraphQlErrorCode.INVALID_INVITE_CODE,
                    "존재하지 않는 초대코드입니다.",
                )

        rejectIfAlreadyJoined(requireNotNull(party.id), currentUserId)
        return party
    }

    private fun validateInviteCode(inviteCode: String) {
        if (inviteCode.length != INVITE_CODE_LENGTH || inviteCode.any { !it.isLetterOrDigit() }) {
            throw GraphQlDomainException(
                GraphQlErrorCode.INVALID_INVITE_CODE,
                "초대코드는 영문과 숫자 6자리여야 합니다.",
            )
        }
    }

    private fun rejectIfAlreadyJoined(
        partyId: Long,
        currentUserId: Long,
    ) {
        if (partyMemberRepository.existsByPartyIdAndServiceUserId(partyId, currentUserId)) {
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
