package udumeoli.tripphoto.feedback.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.feedback.dto.SubmitFeedbackInput
import udumeoli.tripphoto.feedback.entity.Feedback
import udumeoli.tripphoto.feedback.repository.FeedbackRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.service.UserService

class FeedbackServiceTest {
    private lateinit var feedbackRepository: FeedbackRepository
    private lateinit var userService: UserService
    private lateinit var feedbackService: FeedbackService

    @BeforeEach
    fun setUp() {
        feedbackRepository = mockk()
        userService = mockk()
        feedbackService = FeedbackService(feedbackRepository, userService)
        every { userService.getCurrentUser(1L) } returns ServiceUser(id = 1L, nickname = "유저", profileImage = 1L)
    }

    @Test
    fun `피드백을 유저 ID와 함께 저장한다`() {
        val saved = slot<Feedback>()
        every { feedbackRepository.save(capture(saved)) } answers { saved.captured }

        feedbackService.submit(1L, input())

        assertThat(saved.captured.serviceUserId).isEqualTo(1L)
        assertThat(saved.captured.rating).isEqualTo(4)
        assertThat(saved.captured.content).isEqualTo("좋아요")
        assertThat(saved.captured.appVersion).isEqualTo("v 1.0.0")
        assertThat(saved.captured.deviceInfo).isEqualTo("Mozilla/5.0 (iPhone)")
    }

    @Test
    fun `의견은 앞뒤 공백을 털어내고 저장한다`() {
        val saved = slot<Feedback>()
        every { feedbackRepository.save(capture(saved)) } answers { saved.captured }

        feedbackService.submit(1L, input(content = "  \n좋아요 \t"))

        assertThat(saved.captured.content).isEqualTo("좋아요")
    }

    @Test
    fun `별점 경계값 1과 5는 통과한다`() {
        every { feedbackRepository.save(any()) } answers { firstArg() }

        feedbackService.submit(1L, input(rating = 1))
        feedbackService.submit(1L, input(rating = 5))

        verify(exactly = 2) { feedbackRepository.save(any()) }
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 6, -1, Int.MAX_VALUE])
    fun `1~5를 벗어난 별점은 거절한다`(rating: Int) {
        assertValidationError { feedbackService.submit(1L, input(rating = rating)) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   \n\t  "])
    fun `공백뿐인 의견은 거절한다`(content: String) {
        assertValidationError { feedbackService.submit(1L, input(content = content)) }
    }

    @Test
    fun `5000자 의견은 통과하고 5001자는 거절한다`() {
        val saved = slot<Feedback>()
        every { feedbackRepository.save(capture(saved)) } answers { saved.captured }

        feedbackService.submit(1L, input(content = "가".repeat(5_000)))
        assertThat(saved.captured.content).hasSize(5_000)

        assertValidationError { feedbackService.submit(1L, input(content = "가".repeat(5_001))) }
        verify(exactly = 1) { feedbackRepository.save(any()) }
    }

    @Test
    fun `앞뒤 공백을 빼고 5000자면 통과한다`() {
        every { feedbackRepository.save(any()) } answers { firstArg() }

        feedbackService.submit(1L, input(content = " " + "가".repeat(5_000) + " "))

        verify(exactly = 1) { feedbackRepository.save(any()) }
    }

    @Test
    fun `앱 버전과 기기 정보가 컬럼 한도를 넘으면 잘라서 저장한다`() {
        val saved = slot<Feedback>()
        every { feedbackRepository.save(capture(saved)) } answers { saved.captured }

        feedbackService.submit(
            1L,
            input(appVersion = "v".repeat(80), deviceInfo = "u".repeat(1_500)),
        )

        assertThat(saved.captured.appVersion).hasSize(Feedback.APP_VERSION_MAX_LENGTH)
        assertThat(saved.captured.deviceInfo).hasSize(Feedback.DEVICE_INFO_MAX_LENGTH)
    }

    @Test
    fun `존재하지 않는 유저의 토큰이면 저장하지 않고 UNAUTHENTICATED를 던진다`() {
        every { userService.getCurrentUser(2L) } throws
            GraphQlDomainException(GraphQlErrorCode.UNAUTHENTICATED, "로그인이 필요합니다.")

        val thrown = catchThrowable { feedbackService.submit(2L, input()) }

        assertThat(thrown).isInstanceOf(GraphQlDomainException::class.java)
        assertThat((thrown as GraphQlDomainException).code).isEqualTo(GraphQlErrorCode.UNAUTHENTICATED)
        verify(exactly = 0) { feedbackRepository.save(any()) }
    }

    private fun assertValidationError(block: () -> Unit) {
        val thrown = catchThrowable(block)

        assertThat(thrown).isInstanceOf(GraphQlDomainException::class.java)
        assertThat((thrown as GraphQlDomainException).code).isEqualTo(GraphQlErrorCode.VALIDATION_ERROR)
        verify(exactly = 0) { feedbackRepository.save(any()) }
    }

    private fun input(
        rating: Int = 4,
        content: String = "좋아요",
        appVersion: String = "v 1.0.0",
        deviceInfo: String = "Mozilla/5.0 (iPhone)",
    ) = SubmitFeedbackInput(rating, content, appVersion, deviceInfo)
}
