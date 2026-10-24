package udumeoli.tripphoto.auth.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import udumeoli.tripphoto.auth.entity.OAuth2Provider

@Service
class SocialLoginService(
    private val validators: List<SocialTokenValidator>,
    private val authService: AuthService,
) {
    fun validateAndLogin(
        provider: OAuth2Provider,
        token: String,
    ): String {
        val validator =
            validators.firstOrNull { it.supports(provider) }
                ?: throw AuthException(
                    AuthErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "Unsupported OAuth2 provider: $provider",
                )

        val profile = validator.validate(token)
        return authService.prepareOAuthLogin(profile)
    }
}
