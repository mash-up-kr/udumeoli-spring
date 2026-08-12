package udumeoli.tripphoto.user.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.service.PartyCommandService
import udumeoli.tripphoto.user.repository.ServiceUserRepository
import udumeoli.tripphoto.user.repository.SocialAccountRepository

/**
 * 계정 탈퇴.
 * [정책] 업로드한 사진은 모두 삭제한다. 소속된 모든 팟에서 탈퇴 처리되며 팟 자체는 유지된다(공석 전환).
 * 방장이었던 팟은 팟 나가기(leaveParty)와 동일한 정책을 적용한다 — 위임 가능하면 위임, 혼자면 팟 자체를 삭제한다.
 */
@Service
class AccountWithdrawalService(
    private val userService: UserService,
    private val partyMemberRepository: PartyMemberRepository,
    private val partyCommandService: PartyCommandService,
    private val socialAccountRepository: SocialAccountRepository,
    private val serviceUserRepository: ServiceUserRepository,
    private val imageService: ImageService,
) {
    @Transactional
    fun withdraw(currentUserId: Long): Long {
        val user = userService.getCurrentUser(currentUserId)

        val partyIds = partyMemberRepository.findAllByServiceUserId(currentUserId).map { it.partyId }
        partyIds.forEach { partyCommandService.leaveParty(currentUserId, it) }

        imageService.deleteAllUploadedBy(currentUserId)
        socialAccountRepository.deleteAll(socialAccountRepository.findAllByServiceUserId(currentUserId))
        serviceUserRepository.delete(user)

        return currentUserId
    }
}
