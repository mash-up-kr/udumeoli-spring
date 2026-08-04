package udumeoli.tripphoto.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("auth")
data class AuthProperties(
    val jwt: JwtProperties,
    val oauthLoginCodeTtl: Duration = Duration.ofMinutes(2),
    val frontendCallbackUrl: String,
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
