package udumeoli.tripphoto.auth.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import udumeoli.tripphoto.auth.entity.OAuth2Provider

@Component
class GoogleTokenValidator(
    @Value("\${spring.security.oauth2.client.registration.google.client-id}")
    private val googleClientId: String,
) : SocialTokenValidator {
    private val verifier =
        GoogleIdTokenVerifier
            .Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(listOf(googleClientId))
            .build()

    override fun supports(provider: OAuth2Provider): Boolean = provider == OAuth2Provider.GOOGLE

    override fun validate(token: String): SocialProfile {
        val googleIdToken =
            verifier.verify(token)
                ?: throw AuthException(
                    AuthErrorCode.INVALID_TOKEN,
                    HttpStatus.UNAUTHORIZED,
                    "Invalid Google ID token",
                )

        val payload = googleIdToken.payload
        return SocialProfile(
            provider = OAuth2Provider.GOOGLE.registrationId,
            providerUserId = payload.subject,
            email = payload.email,
            profileImageUrl = payload["picture"] as? String,
        )
    }
}
