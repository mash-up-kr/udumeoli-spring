package udumeoli.tripphoto.auth.service

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.stereotype.Component
import udumeoli.tripphoto.auth.config.AuthProperties
import udumeoli.tripphoto.auth.dto.TokenResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Component
class JwtTokenService(
    private val properties: AuthProperties,
) {
    private val clock: Clock = Clock.systemUTC()
    private val secretKey: SecretKey = createSecretKey(properties.jwt.secret)
    private val encoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(secretKey))
    private val decoders = TokenType.entries.associateWith(::createDecoder)

    fun issueTokenPair(serviceUserId: Long): IssuedTokenPair {
        val accessToken = issueUserToken(serviceUserId, TokenType.ACCESS, properties.jwt.accessTokenTtl)
        val refreshToken = issueUserToken(serviceUserId, TokenType.REFRESH, properties.jwt.refreshTokenTtl)
        return IssuedTokenPair(
            response =
                TokenResponse(
                    accessToken = accessToken.value,
                    refreshToken = refreshToken.value,
                    accessTokenExpiresIn = properties.jwt.accessTokenTtl.seconds,
                ),
            refreshExpiresAt = refreshToken.expiresAt,
        )
    }

    fun issueSignupToken(profile: SocialProfile): String {
        val now = clock.instant()
        val builder =
            JwtClaimsSet
                .builder()
                .issuer(properties.jwt.issuer)
                .subject(profile.providerUserId)
                .issuedAt(now)
                .expiresAt(now.plus(properties.jwt.signupTokenTtl))
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, TokenType.SIGNUP.name)
                .claim(PROVIDER_CLAIM, profile.provider)

        profile.email?.let { builder.claim(EMAIL_CLAIM, it) }
        profile.profileImageUrl?.let { builder.claim(PROFILE_IMAGE_URL_CLAIM, it) }
        return encode(builder.build())
    }

    fun decodeAccessToken(token: String): Jwt = decode(token, TokenType.ACCESS)

    fun decodeRefreshToken(token: String): UserTokenClaims {
        val jwt = decode(token, TokenType.REFRESH)
        return UserTokenClaims(requireUserId(jwt), requireNotNull(jwt.expiresAt))
    }

    fun decodeSignupToken(token: String): SocialProfile {
        val jwt = decode(token, TokenType.SIGNUP)
        return SocialProfile(
            provider = requireStringClaim(jwt, PROVIDER_CLAIM),
            providerUserId = jwt.subject.takeUnless { it.isNullOrBlank() } ?: invalidToken(),
            email = jwt.getClaimAsString(EMAIL_CLAIM),
            profileImageUrl = jwt.getClaimAsString(PROFILE_IMAGE_URL_CLAIM),
        )
    }

    fun accessTokenDecoder(): JwtDecoder = decoders.getValue(TokenType.ACCESS)

    private fun issueUserToken(
        serviceUserId: Long,
        type: TokenType,
        ttl: Duration,
    ): IssuedJwt {
        val now = clock.instant()
        val expiresAt = now.plus(ttl)
        val claims =
            JwtClaimsSet
                .builder()
                .issuer(properties.jwt.issuer)
                .subject(serviceUserId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, type.name)
                .claim(ROLES_CLAIM, listOf("USER"))
                .build()
        return IssuedJwt(encode(claims), expiresAt)
    }

    private fun encode(claims: JwtClaimsSet): String =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    claims,
                ),
            ).tokenValue

    private fun decode(
        token: String,
        type: TokenType,
    ): Jwt =
        try {
            decoders.getValue(type).decode(token)
        } catch (exception: JwtException) {
            throw AuthException(
                AuthErrorCode.INVALID_TOKEN,
                HttpStatus.UNAUTHORIZED,
                "유효하지 않거나 만료된 인증 토큰입니다.",
                exception,
            )
        }

    private fun createDecoder(type: TokenType): JwtDecoder {
        val decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build()
        val typeValidator: OAuth2TokenValidator<Jwt> =
            JwtClaimValidator(TOKEN_TYPE_CLAIM) { value: String? -> value == type.name }
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(properties.jwt.issuer),
                typeValidator,
            ),
        )
        return decoder
    }

    private fun requireUserId(jwt: Jwt): Long =
        jwt.subject.toLongOrNull()
            ?: throw AuthException(
                AuthErrorCode.INVALID_TOKEN,
                HttpStatus.UNAUTHORIZED,
                "인증 토큰의 사용자 정보가 올바르지 않습니다.",
            )

    private fun requireStringClaim(
        jwt: Jwt,
        claim: String,
    ): String = jwt.getClaimAsString(claim)?.takeUnless(String::isBlank) ?: invalidToken()

    private fun invalidToken(): Nothing =
        throw AuthException(
            AuthErrorCode.INVALID_TOKEN,
            HttpStatus.UNAUTHORIZED,
            "인증 토큰의 필수 정보가 올바르지 않습니다.",
        )

    private fun createSecretKey(rawSecret: String): SecretKey {
        require(rawSecret.isNotBlank()) { "auth.jwt.secret must not be blank" }
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(rawSecret.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, "HmacSHA256")
    }

    private enum class TokenType {
        ACCESS,
        REFRESH,
        SIGNUP,
    }

    companion object {
        private const val TOKEN_TYPE_CLAIM = "token_type"
        private const val PROVIDER_CLAIM = "provider"
        private const val EMAIL_CLAIM = "email"
        private const val PROFILE_IMAGE_URL_CLAIM = "profile_image_url"
        private const val ROLES_CLAIM = "roles"
    }
}

data class SocialProfile(
    val provider: String,
    val providerUserId: String,
    val email: String? = null,
    val profileImageUrl: String? = null,
)

data class UserTokenClaims(
    val serviceUserId: Long,
    val expiresAt: Instant,
)

data class IssuedTokenPair(
    val response: TokenResponse,
    val refreshExpiresAt: Instant,
)

private data class IssuedJwt(
    val value: String,
    val expiresAt: Instant,
)
