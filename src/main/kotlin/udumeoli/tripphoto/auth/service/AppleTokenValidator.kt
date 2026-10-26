package udumeoli.tripphoto.auth.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import udumeoli.tripphoto.auth.entity.OAuth2Provider
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

@Component
class AppleTokenValidator(
    @Value("\${spring.security.oauth2.client.registration.apple.client-id}")
    private val appleClientId: String,
    private val objectMapper: ObjectMapper,
    private val restTemplate: RestTemplate = RestTemplate(),
) : SocialTokenValidator {
    companion object {
        private const val APPLE_PUBLIC_KEYS_URL = "https://appleid.apple.com/auth/keys"
        private const val APPLE_ISSUER = "https://appleid.apple.com"
    }

    override fun supports(provider: OAuth2Provider): Boolean = provider == OAuth2Provider.APPLE

    override fun validate(token: String): SocialProfile {
        val publicKey = getApplePublicKey(token)

        val claims =
            try {
                Jwts
                    .parser()
                    .verifyWith(publicKey as java.security.interfaces.RSAPublicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .requireAudience(appleClientId)
                    .build()
                    .parseSignedClaims(token)
                    .payload
            } catch (e: JwtException) {
                throw AuthException(
                    AuthErrorCode.INVALID_TOKEN,
                    HttpStatus.UNAUTHORIZED,
                    "Invalid Apple identity token: ${e.message}",
                    e,
                )
            }

        return SocialProfile(
            provider = OAuth2Provider.APPLE.registrationId,
            providerUserId = claims.subject,
            email = claims["email"] as? String,
            profileImageUrl = null,
        )
    }

    private fun getApplePublicKey(token: String): PublicKey {
        val kid = extractKidFromToken(token)
        val jwks = fetchApplePublicKeys()

        val key =
            jwks["keys"]
                ?.let { it as? List<*> }
                ?.firstOrNull { (it as? Map<*, *>)?.get("kid") == kid }
                ?.let { it as? Map<*, *> }
                ?: throw AuthException(
                    AuthErrorCode.INVALID_TOKEN,
                    HttpStatus.UNAUTHORIZED,
                    "No matching public key found for kid: $kid",
                )

        return createPublicKey(key)
    }

    private fun extractKidFromToken(token: String): String {
        val header = token.split(".")[0]
        val decodedHeader = String(Base64.getUrlDecoder().decode(header))
        val headerMap = objectMapper.readValue(decodedHeader, Map::class.java)
        return headerMap["kid"] as? String
            ?: throw AuthException(
                AuthErrorCode.INVALID_TOKEN,
                HttpStatus.UNAUTHORIZED,
                "No kid found in token header",
            )
    }

    private fun fetchApplePublicKeys(): Map<*, *> =
        try {
            restTemplate.getForObject(APPLE_PUBLIC_KEYS_URL, Map::class.java)
                ?: throw AuthException(
                    AuthErrorCode.EXTERNAL_API_ERROR,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Failed to fetch Apple public keys",
                )
        } catch (e: RestClientException) {
            throw AuthException(
                AuthErrorCode.EXTERNAL_API_ERROR,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Failed to fetch Apple public keys: ${e.message}",
                e,
            )
        }

    private fun createPublicKey(key: Map<*, *>): PublicKey {
        val n =
            key["n"] as? String
                ?: throw AuthException(
                    AuthErrorCode.INVALID_TOKEN,
                    HttpStatus.UNAUTHORIZED,
                    "Missing 'n' in public key",
                )
        val e =
            key["e"] as? String
                ?: throw AuthException(
                    AuthErrorCode.INVALID_TOKEN,
                    HttpStatus.UNAUTHORIZED,
                    "Missing 'e' in public key",
                )

        val modulus = BigInteger(1, Base64.getUrlDecoder().decode(n))
        val exponent = BigInteger(1, Base64.getUrlDecoder().decode(e))

        val spec = RSAPublicKeySpec(modulus, exponent)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(spec)
    }
}
