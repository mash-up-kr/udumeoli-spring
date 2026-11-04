@file:Suppress("MaxLineLength", "MagicNumber")

package udumeoli.tripphoto.feedback

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.feedback.repository.FeedbackRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("local")
class FeedbackGraphQlTest {
    @Autowired lateinit var graphQlService: ExecutionGraphQlService

    @Autowired lateinit var serviceUserRepository: ServiceUserRepository

    @Autowired lateinit var feedbackRepository: FeedbackRepository

    @BeforeEach
    fun setUp() {
        feedbackRepository.deleteAll()
        serviceUserRepository.deleteAll()
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `피드백을 전송하면 true를 받고 유저 ID와 전송 시각이 서버에서 채워져 저장된다`() {
        val user = createUser("유저")
        val before = LocalDateTime.now().minusSeconds(5)

        graphQlTester(user)
            .document(submit(rating = 5, content = "정말 좋아요", appVersion = "v 1.0.0", deviceInfo = "Mozilla/5.0 (iPhone)"))
            .execute()
            .path("submitFeedback")
            .entity(Boolean::class.java)
            .isEqualTo(true)

        val saved = feedbackRepository.findAll().single()
        assertThat(saved.serviceUserId).isEqualTo(user.id)
        assertThat(saved.rating).isEqualTo(5)
        assertThat(saved.content).isEqualTo("정말 좋아요")
        assertThat(saved.appVersion).isEqualTo("v 1.0.0")
        assertThat(saved.deviceInfo).isEqualTo("Mozilla/5.0 (iPhone)")
        assertThat(saved.createdAt).isAfter(before)
    }

    @Test
    fun `5000자 의견도 CLOB 컬럼에 그대로 저장된다`() {
        val user = createUser("유저")
        val content = "가나다라마".repeat(1_000)

        graphQlTester(user)
            .document(submit(content = content))
            .execute()
            .path("submitFeedback")
            .entity(Boolean::class.java)
            .isEqualTo(true)

        assertThat(feedbackRepository.findAll().single().content).isEqualTo(content)
    }

    @Test
    fun `범위를 벗어난 별점은 VALIDATION_ERROR로 거절되고 저장되지 않는다`() {
        val user = createUser("유저")

        listOf(0, 6).forEach { rating ->
            graphQlTester(user)
                .document(submit(rating = rating))
                .execute()
                .errors()
                .expect { error -> error.extensions["code"] == GraphQlErrorCode.VALIDATION_ERROR.name }
                .verify()
        }

        assertThat(feedbackRepository.findAll()).isEmpty()
    }

    @Test
    fun `공백뿐이거나 5001자인 의견은 VALIDATION_ERROR로 거절되고 저장되지 않는다`() {
        val user = createUser("유저")

        listOf("   ", "가".repeat(5_001)).forEach { content ->
            graphQlTester(user)
                .document(submit(content = content))
                .execute()
                .errors()
                .expect { error -> error.extensions["code"] == GraphQlErrorCode.VALIDATION_ERROR.name }
                .verify()
        }

        assertThat(feedbackRepository.findAll()).isEmpty()
    }

    @Test
    fun `로그인하지 않으면 UNAUTHENTICATED로 거절되고 저장되지 않는다`() {
        ExecutionGraphQlServiceTester
            .builder(graphQlService)
            .build()
            .document(submit())
            .execute()
            .errors()
            .expect { error -> error.extensions["code"] == GraphQlErrorCode.UNAUTHENTICATED.name }
            .verify()

        assertThat(feedbackRepository.findAll()).isEmpty()
    }

    private fun submit(
        rating: Int = 4,
        content: String = "좋아요",
        appVersion: String = "v 1.0.0",
        deviceInfo: String = "test-agent",
    ): String =
        """
        mutation {
          submitFeedback(input: {
            rating: $rating
            content: "$content"
            appVersion: "$appVersion"
            deviceInfo: "$deviceInfo"
          })
        }
        """.trimIndent()

    private fun createUser(nickname: String): ServiceUser = serviceUserRepository.save(ServiceUser(nickname = nickname, profileImage = 1L))

    private fun graphQlTester(user: ServiceUser): ExecutionGraphQlServiceTester {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(requireNotNull(user.id).toString(), null, "ROLE_USER")
        return ExecutionGraphQlServiceTester
            .builder(graphQlService)
            .build()
    }
}
