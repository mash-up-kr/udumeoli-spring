package udumeoli.tripphoto.user.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository
import java.util.Optional

class UserServiceTest {
    private lateinit var serviceUserRepository: ServiceUserRepository
    private lateinit var imageService: ImageService
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        serviceUserRepository = mockk()
        imageService = mockk()
        userService = UserService(serviceUserRepository, imageService)
    }

    @Test
    fun `내 프로필을 조회한다`() {
        every { serviceUserRepository.findById(1L) } returns Optional.of(user())

        val result = userService.me(1)

        assertThat(result.id).isEqualTo(1)
        assertThat(result.nickname).isEqualTo("기존")
        assertThat(result.profileImage).isEqualTo(1)
    }

    @Test
    fun `프로필을 수정한다`() {
        val savedUserSlot = slot<ServiceUser>()

        every { serviceUserRepository.findById(1L) } returns Optional.of(user())
        every { serviceUserRepository.save(capture(savedUserSlot)) } answers {
            savedUserSlot.captured
        }

        val result = userService.updateProfile(currentUserId = 1, nickname = "변경", profileImage = 2)

        assertThat(result.nickname).isEqualTo("변경")
        assertThat(result.profileImage).isEqualTo(2)
        assertThat(savedUserSlot.captured.id).isEqualTo(1)
    }

    @Test
    fun `닉네임 앞뒤 공백은 털어내고 저장한다`() {
        val savedUserSlot = slot<ServiceUser>()
        every { serviceUserRepository.findById(1L) } returns Optional.of(user())
        every { serviceUserRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }

        val result = userService.updateProfile(currentUserId = 1, nickname = "  민지  ", profileImage = null)

        assertThat(result.nickname).isEqualTo("민지")
        assertThat(savedUserSlot.captured.nickname).isEqualTo("민지")
    }

    @Test
    fun `가운데 공백은 닉네임으로 인정하고 길이에 포함한다`() {
        val savedUserSlot = slot<ServiceUser>()
        every { serviceUserRepository.findById(1L) } returns Optional.of(user())
        every { serviceUserRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }

        val result = userService.updateProfile(currentUserId = 1, nickname = "김 민 지", profileImage = null)

        assertThat(result.nickname).isEqualTo("김 민 지")
    }

    @Test
    fun `공백만 입력한 닉네임은 거절한다`() {
        val thrown =
            catchThrowable {
                userService.updateProfile(currentUserId = 1, nickname = "      ", profileImage = null)
            }

        assertThat(thrown).isInstanceOf(GraphQlDomainException::class.java)
        assertThat((thrown as GraphQlDomainException).code).isEqualTo(GraphQlErrorCode.VALIDATION_ERROR)
        verify(exactly = 0) { serviceUserRepository.save(any()) }
    }

    @Test
    fun `6자를 넘는 닉네임은 거절한다`() {
        val thrown =
            catchThrowable {
                userService.updateProfile(currentUserId = 1, nickname = "일곱자짜리닉네임", profileImage = null)
            }

        assertThat(thrown).isInstanceOf(GraphQlDomainException::class.java)
        assertThat((thrown as GraphQlDomainException).code).isEqualTo(GraphQlErrorCode.VALIDATION_ERROR)
        verify(exactly = 0) { serviceUserRepository.save(any()) }
    }

    @Test
    fun `딱 6자인 닉네임은 통과한다`() {
        val savedUserSlot = slot<ServiceUser>()
        every { serviceUserRepository.findById(1L) } returns Optional.of(user())
        every { serviceUserRepository.save(capture(savedUserSlot)) } answers { savedUserSlot.captured }

        val result = userService.updateProfile(currentUserId = 1, nickname = "여섯자짜리닉", profileImage = null)

        assertThat(result.nickname).isEqualTo("여섯자짜리닉")
    }

    @Test
    fun `현재 사용자를 찾을 수 없으면 인증 오류를 반환한다`() {
        every { serviceUserRepository.findById(1L) } returns Optional.empty()

        val thrown =
            catchThrowable {
                userService.getCurrentUser(1)
            }

        assertThat(thrown).isInstanceOf(GraphQlDomainException::class.java)
        assertThat((thrown as GraphQlDomainException).code).isEqualTo(GraphQlErrorCode.UNAUTHENTICATED)
    }

    private fun user(): ServiceUser = ServiceUser(id = 1, nickname = "기존", profileImage = 1)
}
