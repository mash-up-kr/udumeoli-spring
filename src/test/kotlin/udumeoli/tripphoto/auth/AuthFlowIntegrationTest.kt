package udumeoli.tripphoto.auth

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import udumeoli.tripphoto.auth.dto.AuthFlowStatus
import udumeoli.tripphoto.auth.repository.RefreshTokenRepository
import udumeoli.tripphoto.auth.service.AuthException
import udumeoli.tripphoto.auth.service.AuthService
import udumeoli.tripphoto.auth.service.JwtTokenService
import udumeoli.tripphoto.auth.service.SocialProfile
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.user.repository.ServiceUserRepository
import udumeoli.tripphoto.user.repository.SocialAccountRepository

@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:authflow;MODE=Oracle;DB_CLOSE_DELAY=-1"])
@ActiveProfiles("local")
class AuthFlowIntegrationTest {
    @Autowired lateinit var authService: AuthService

    @Autowired lateinit var jwtTokenService: JwtTokenService

    @Autowired lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired lateinit var socialAccountRepository: SocialAccountRepository

    @Autowired lateinit var serviceUserRepository: ServiceUserRepository

    @Autowired lateinit var imageRepository: ImageRepository

    @BeforeEach
    fun setUp() {
        cleanUp()
    }

    @AfterEach
    fun tearDown() {
        cleanUp()
    }

    private fun cleanUp() {
        refreshTokenRepository.deleteAll()
        socialAccountRepository.deleteAll()
        serviceUserRepository.deleteAll()
        imageRepository.deleteAll()
    }

    private fun uploadedProfileImageId(): Long =
        requireNotNull(
            imageRepository
                .save(
                    Image(
                        objectKey = "original/${java.util.UUID.randomUUID()}.jpg",
                        originalUrl = "https://example.com/profile.jpg",
                    ),
                ).id,
        )

    @Test
    fun `신규 소셜 사용자는 닉네임 입력 후 가입하고 앱 토큰을 받는다`() {
        val code =
            authService.prepareOAuthLogin(
                SocialProfile("kakao", "kakao-123"),
            )

        val exchange = authService.exchangeLoginCode(code)
        assertThat(exchange.status).isEqualTo(AuthFlowStatus.SIGNUP_REQUIRED)
        assertThat(exchange.tokens).isNull()

        val tokens =
            authService.completeSignup(
                signupToken = requireNotNull(exchange.signupToken),
                nickname = "  직접 입력한 닉네임  ",
                profileImage = uploadedProfileImageId(),
            )
        val accessJwt = jwtTokenService.decodeAccessToken(tokens.accessToken)

        assertThat(serviceUserRepository.findById(accessJwt.subject.toLong()).orElseThrow().nickname).isEqualTo("직접 입력한 닉네임")
        assertThat(socialAccountRepository.findByProviderAndProviderUserId("kakao", "kakao-123")?.providerEmail)
            .isNull()
        assertThat(refreshTokenRepository.findAll()).hasSize(1)
    }

    @Test
    fun `기존 소셜 사용자는 즉시 로그인하고 refresh token은 한 번만 사용할 수 있다`() {
        val signupCode = authService.prepareOAuthLogin(SocialProfile("kakao", "kakao-456"))
        val signupToken = requireNotNull(authService.exchangeLoginCode(signupCode).signupToken)
        authService.completeSignup(
            signupToken = signupToken,
            nickname = "회원",
            profileImage = uploadedProfileImageId(),
        )

        val loginCode = authService.prepareOAuthLogin(SocialProfile("kakao", "kakao-456"))
        assertThat(refreshTokenRepository.findAll()).hasSize(1)

        val login = authService.exchangeLoginCode(loginCode)
        assertThat(login.status).isEqualTo(AuthFlowStatus.AUTHENTICATED)
        assertThat(refreshTokenRepository.findAll()).hasSize(2)

        val originalRefreshToken = requireNotNull(login.tokens).refreshToken
        val rotated = authService.refresh(originalRefreshToken)
        assertThat(rotated.refreshToken).isNotEqualTo(originalRefreshToken)
        assertThatThrownBy { authService.refresh(originalRefreshToken) }.isInstanceOf(AuthException::class.java)
    }

    @Test
    fun `로그인 교환 코드는 한 번만 사용할 수 있다`() {
        val code = authService.prepareOAuthLogin(SocialProfile("kakao", "one-time"))
        authService.exchangeLoginCode(code)

        assertThatThrownBy { authService.exchangeLoginCode(code) }.isInstanceOf(AuthException::class.java)
    }

    @Test
    fun `허용되지 않는 contentType으로 회원가입용 업로드 URL을 요청하면 AuthException으로 실패한다`() {
        val code = authService.prepareOAuthLogin(SocialProfile("kakao", "bad-content-type"))
        val signupToken = requireNotNull(authService.exchangeLoginCode(code).signupToken)

        assertThatThrownBy { authService.createSignupImageUploadUrl(signupToken, "image/gif") }
            .isInstanceOf(AuthException::class.java)
    }

    @Test
    fun `신규 소셜 사용자는 로그인 코드 교환 전까지 signup token을 발급하지 않는다`() {
        val code = authService.prepareOAuthLogin(SocialProfile("kakao", "signup-later"))

        assertThat(refreshTokenRepository.findAll()).isEmpty()
        assertThat(serviceUserRepository.findAll()).isEmpty()

        val exchange = authService.exchangeLoginCode(code)

        assertThat(exchange.status).isEqualTo(AuthFlowStatus.SIGNUP_REQUIRED)
        assertThat(exchange.signupToken).isNotBlank()
        assertThat(refreshTokenRepository.findAll()).isEmpty()
        assertThat(serviceUserRepository.findAll()).isEmpty()
    }
}
