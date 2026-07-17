package udumeoli.tripphoto.user.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository
import java.util.Optional

class UserServiceTest {
    private lateinit var serviceUserRepository: ServiceUserRepository
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        serviceUserRepository = mockk()
        userService = UserService(serviceUserRepository)
    }

    @Test
    fun `내 프로필을 조회한다`() {
        every { serviceUserRepository.findById(1L) } returns Optional.of(user())

        val result = userService.me(1)

        assertThat(result.id).isEqualTo(1)
        assertThat(result.nickname).isEqualTo("기존")
        assertThat(result.profileImageUrl).isEqualTo("old.png")
    }

    @Test
    fun `프로필을 수정한다`() {
        val savedUserSlot = slot<ServiceUser>()

        every { serviceUserRepository.findById(1L) } returns Optional.of(user())
        every { serviceUserRepository.save(capture(savedUserSlot)) } answers {
            savedUserSlot.captured
        }

        val result = userService.updateProfile(currentUserId = 1, nickname = "변경", profileImageUrl = "new.png")

        assertThat(result.nickname).isEqualTo("변경")
        assertThat(result.profileImageUrl).isEqualTo("new.png")
        assertThat(savedUserSlot.captured.id).isEqualTo(1)
    }

    @Test
    fun `현재 사용자를 찾을 수 없으면 인증 오류를 반환한다`() {
        every { serviceUserRepository.findById(1L) } returns Optional.empty()

        val thrown =
            catchThrowable {
                userService.currentUser(1)
            }

        assertThat(thrown).isInstanceOf(GraphQlDomainException::class.java)
        assertThat((thrown as GraphQlDomainException).code).isEqualTo(GraphQlErrorCode.UNAUTHENTICATED)
    }

    private fun user(): ServiceUser = ServiceUser(id = 1, nickname = "기존", profileImageUrl = "old.png")
}
