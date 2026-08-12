package udumeoli.tripphoto.user.service

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.service.PartyCommandService
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.entity.SocialAccount
import udumeoli.tripphoto.user.repository.ServiceUserRepository
import udumeoli.tripphoto.user.repository.SocialAccountRepository

class AccountWithdrawalServiceTest {
    private lateinit var userService: UserService
    private lateinit var partyMemberRepository: PartyMemberRepository
    private lateinit var partyCommandService: PartyCommandService
    private lateinit var socialAccountRepository: SocialAccountRepository
    private lateinit var serviceUserRepository: ServiceUserRepository
    private lateinit var imageService: ImageService
    private lateinit var accountWithdrawalService: AccountWithdrawalService

    @BeforeEach
    fun setUp() {
        userService = mockk()
        partyMemberRepository = mockk()
        partyCommandService = mockk()
        socialAccountRepository = mockk()
        serviceUserRepository = mockk()
        imageService = mockk()

        accountWithdrawalService =
            AccountWithdrawalService(
                userService = userService,
                partyMemberRepository = partyMemberRepository,
                partyCommandService = partyCommandService,
                socialAccountRepository = socialAccountRepository,
                serviceUserRepository = serviceUserRepository,
                imageService = imageService,
            )
    }

    @Test
    fun `탈퇴 시 소속된 모든 팟에서 나가고, 업로드한 사진과 소셜 연동, 계정을 모두 삭제한다`() {
        val user = ServiceUser(id = 1, nickname = "탈퇴자", profileImage = 1)
        val memberships =
            listOf(
                PartyMember(id = 100, partyId = 10, serviceUserId = 1),
                PartyMember(id = 200, partyId = 20, serviceUserId = 1),
            )
        val socialAccounts = listOf(SocialAccount(id = 900, serviceUserId = 1, provider = "kakao", providerUserId = "abc"))

        every { userService.getCurrentUser(1L) } returns user
        every { partyMemberRepository.findAllByServiceUserId(1L) } returns memberships
        every { partyCommandService.leaveParty(1L, 10L) } returns 10L
        every { partyCommandService.leaveParty(1L, 20L) } returns 20L
        every { imageService.deleteAllUploadedBy(1L) } just Runs
        every { socialAccountRepository.findAllByServiceUserId(1L) } returns socialAccounts
        every { socialAccountRepository.deleteAll(socialAccounts) } just Runs
        every { serviceUserRepository.delete(user) } just Runs

        val result = accountWithdrawalService.withdraw(currentUserId = 1)

        assertThat(result).isEqualTo(1L)
        verify { partyCommandService.leaveParty(1L, 10L) }
        verify { partyCommandService.leaveParty(1L, 20L) }
        verifyOrder {
            imageService.deleteAllUploadedBy(1L)
            socialAccountRepository.deleteAll(socialAccounts)
            serviceUserRepository.delete(user)
        }
    }

    @Test
    fun `소속된 팟이 없어도 사진 삭제와 계정 삭제는 그대로 진행된다`() {
        val user = ServiceUser(id = 1, nickname = "탈퇴자", profileImage = 1)

        every { userService.getCurrentUser(1L) } returns user
        every { partyMemberRepository.findAllByServiceUserId(1L) } returns emptyList()
        every { imageService.deleteAllUploadedBy(1L) } just Runs
        every { socialAccountRepository.findAllByServiceUserId(1L) } returns emptyList()
        every { socialAccountRepository.deleteAll(emptyList<SocialAccount>()) } just Runs
        every { serviceUserRepository.delete(user) } just Runs

        val result = accountWithdrawalService.withdraw(currentUserId = 1)

        assertThat(result).isEqualTo(1L)
        verify(exactly = 0) { partyCommandService.leaveParty(any(), any()) }
        verify { serviceUserRepository.delete(user) }
    }
}
