@file:Suppress("LongParameterList", "TooManyFunctions", "ThrowsCount", "MaxLineLength", "MagicNumber", "ForbiddenComment")

package udumeoli.tripphoto.auth.service

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.annotation.Description
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import udumeoli.tripphoto.auth.config.AuthProperties
import java.security.SecureRandom
import java.util.Base64

@Component
@Description("OAuth 로그인 결과 교환용 1회용 코드 저장소")
class OAuthLoginCodeStore(
    properties: AuthProperties,
) {
    private val random = SecureRandom()
    private val cache =
        Caffeine
            .newBuilder()
            .expireAfterWrite(properties.oauthLoginCodeTtl)
            .maximumSize(10_000)
            .build<String, LoginCodePayload>()

    fun issue(payload: LoginCodePayload): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        cache.put(code, payload)
        return code
    }

    fun consume(code: String): LoginCodePayload =
        cache.asMap().remove(code)
            ?: throw AuthException(
                AuthErrorCode.INVALID_LOGIN_CODE,
                HttpStatus.UNAUTHORIZED,
                "로그인 코드가 유효하지 않거나 만료되었습니다.",
            )
}

sealed interface LoginCodePayload {
    data class Authenticated(
        val serviceUserId: Long,
    ) : LoginCodePayload

    data class SignupRequired(
        val provider: String,
        val providerUserId: String,
    ) : LoginCodePayload
}
