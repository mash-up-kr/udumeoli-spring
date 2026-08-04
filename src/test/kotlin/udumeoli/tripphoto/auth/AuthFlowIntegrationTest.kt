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
    }

    @Test
    fun `신규 소셜 사용자는 닉네임 입력 후 가입하고 앱 토큰을 받는다`() {
        val code =
            authService.prepareOAuthLogin(
                SocialProfile("kakao", "kakao-123", "member@example.com", "profile.png"),
            )

        val exchange = authService.exchangeLoginCode(code)
        assertThat(exchange.status).isEqualTo(AuthFlowStatus.SIGNUP_REQUIRED)
        assertThat(exchange.tokens).isNull()

        val tokens = authService.completeSignup(requireNotNull(exchange.signupToken), "  직접 입력한 닉네임  ")
        val accessJwt = jwtTokenService.decodeAccessToken(tokens.accessToken)

        assertThat(serviceUserRepository.findById(accessJwt.subject.toLong()).orElseThrow().nickname).isEqualTo("직접 입력한 닉네임")
        assertThat(socialAccountRepository.findByProviderAndProviderUserId("kakao", "kakao-123")?.providerEmail)
            .isEqualTo("member@example.com")
        assertThat(refreshTokenRepository.findAll()).hasSize(1)
    }

    @Test
    fun `기존 소셜 사용자는 즉시 로그인하고 refresh token은 한 번만 사용할 수 있다`() {
        val signupCode = authService.prepareOAuthLogin(SocialProfile("kakao", "kakao-456"))
        val signupToken = requireNotNull(authService.exchangeLoginCode(signupCode).signupToken)
        authService.completeSignup(signupToken, "회원")

        val loginCode = authService.prepareOAuthLogin(SocialProfile("kakao", "kakao-456"))
        val login = authService.exchangeLoginCode(loginCode)
        assertThat(login.status).isEqualTo(AuthFlowStatus.AUTHENTICATED)

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
}
