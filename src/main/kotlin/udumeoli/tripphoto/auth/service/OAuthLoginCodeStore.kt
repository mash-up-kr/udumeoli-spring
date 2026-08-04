package udumeoli.tripphoto.auth.service

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.annotation.Description
import org.springframework.stereotype.Component
import udumeoli.tripphoto.auth.config.AuthProperties
import udumeoli.tripphoto.auth.dto.LoginCodeExchangeResponse
import java.security.SecureRandom
import java.util.Base64

@Component
@Description("JWT 임시 저장용 캐시")
class OAuthLoginCodeStore(
    properties: AuthProperties,
) {
    private val random = SecureRandom()
    private val cache =
        Caffeine
            .newBuilder()
            .expireAfterWrite(properties.oauthLoginCodeTtl)
            .maximumSize(10_000)
            .build<String, LoginCodeExchangeResponse>()

    fun issue(result: LoginCodeExchangeResponse): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        cache.put(code, result)
        return code
    }

    fun consume(code: String): LoginCodeExchangeResponse =
        cache.asMap().remove(code)
            ?: throw AuthException(
                AuthErrorCode.INVALID_LOGIN_CODE,
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "로그인 코드가 유효하지 않거나 만료되었습니다.",
            )
}
