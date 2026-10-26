package udumeoli.tripphoto.auth.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.cors.DefaultCorsProcessor
import java.time.Duration

class SecurityConfigTest {
    private val frontendOrigin = "https://www.pinnned.co.kr"
    private val appleOrigin = "https://appleid.apple.com"
    private val source = SecurityConfig().corsConfigurationSource(authProperties())

    @Test
    fun `Apple form_post origin은 Apple OAuth 콜백에서 허용된다`() {
        val request =
            MockHttpServletRequest("POST", "/login/oauth2/code/apple").apply {
                addHeader(HttpHeaders.ORIGIN, appleOrigin)
            }
        val response = MockHttpServletResponse()
        val configuration =
            source.getCorsConfiguration(request)

        assertThat(configuration).isNotNull
        assertThat(configuration?.checkOrigin(appleOrigin)).isEqualTo(appleOrigin)
        assertThat(configuration?.checkHttpMethod(HttpMethod.POST)).containsExactly(HttpMethod.POST)
        assertThat(DefaultCorsProcessor().processRequest(configuration, request, response)).isTrue()
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(appleOrigin)
    }

    @Test
    fun `Apple origin은 다른 API 경로에서 허용되지 않는다`() {
        val configuration =
            source.getCorsConfiguration(MockHttpServletRequest("POST", "/api/auth/login/apple"))

        assertThat(configuration).isNotNull
        assertThat(configuration?.checkOrigin(appleOrigin)).isNull()
    }

    @Test
    fun `기존 프런트엔드 origin은 일반 API 경로에서 계속 허용된다`() {
        val configuration =
            source.getCorsConfiguration(MockHttpServletRequest("POST", "/api/auth/refresh"))

        assertThat(configuration).isNotNull
        assertThat(configuration?.checkOrigin(frontendOrigin)).isEqualTo(frontendOrigin)
    }

    private fun authProperties(): AuthProperties =
        AuthProperties(
            jwt =
                AuthProperties.JwtProperties(
                    issuer = "test",
                    secret = "test-secret",
                    accessTokenTtl = Duration.ofMinutes(30),
                    refreshTokenTtl = Duration.ofDays(14),
                    signupTokenTtl = Duration.ofMinutes(10),
                ),
            frontendCallbackUrl = "$frontendOrigin/login/callback",
            corsAllowedOrigins = listOf(frontendOrigin),
        )
}
