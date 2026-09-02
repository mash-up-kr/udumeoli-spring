@file:Suppress("LongParameterList", "TooManyFunctions", "ThrowsCount", "MaxLineLength", "MagicNumber", "ForbiddenComment")

package udumeoli.tripphoto.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("auth")
data class AuthProperties(
    val jwt: JwtProperties,
    val oauthLoginCodeTtl: Duration = Duration.ofMinutes(2),
    val frontendCallbackUrl: String,
    // frontendCallbackUrl 대신 쓸 수 있는 값들의 화이트리스트(로컬 개발용 등).
    // /oauth2/authorization/{registrationId} 요청의 frontendRedirect 쿼리파라미터가 이 목록에 있을 때만 허용한다.
    val allowedFrontendCallbackUrls: List<String> = emptyList(),
    val corsAllowedOrigins: List<String> = emptyList(),
) {
    data class JwtProperties(
        val issuer: String,
        val secret: String,
        val accessTokenTtl: Duration = Duration.ofMinutes(30),
        val refreshTokenTtl: Duration = Duration.ofDays(14),
        val signupTokenTtl: Duration = Duration.ofMinutes(10),
    )
}
