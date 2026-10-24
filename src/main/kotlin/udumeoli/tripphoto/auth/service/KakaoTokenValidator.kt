@file:Suppress("ThrowsCount")

package udumeoli.tripphoto.auth.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import udumeoli.tripphoto.auth.entity.OAuth2Provider

@Component
class KakaoTokenValidator(
    private val restTemplate: RestTemplate = RestTemplate(),
) : SocialTokenValidator {
    companion object {
        private const val KAKAO_TOKEN_INFO_URL = "https://kapi.kakao.com/v1/user/access_token_info"
        private const val KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me"
    }

    override fun supports(provider: OAuth2Provider): Boolean = provider == OAuth2Provider.KAKAO

    override fun validate(token: String): SocialProfile {
        // 1. 토큰 유효성 검증
        validateTokenInfo(token)

        // 2. 사용자 정보 가져오기
        return fetchUserInfo(token)
    }

    private fun validateTokenInfo(accessToken: String) {
        try {
            val headers = org.springframework.http.HttpHeaders()
            headers.set("Authorization", "Bearer $accessToken")

            val entity = org.springframework.http.HttpEntity<String>(headers)
            val response =
                restTemplate.exchange(
                    KAKAO_TOKEN_INFO_URL,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Map::class.java,
                )

            // 200 OK가 아니면 예외 발생
            if (!response.statusCode.is2xxSuccessful) {
                throw AuthException(
                    AuthErrorCode.INVALID_TOKEN,
                    HttpStatus.UNAUTHORIZED,
                    "Invalid Kakao access token",
                )
            }
        } catch (e: RestClientException) {
            throw AuthException(
                AuthErrorCode.INVALID_TOKEN,
                HttpStatus.UNAUTHORIZED,
                "Failed to validate Kakao token: ${e.message}",
                e,
            )
        }
    }

    private fun fetchUserInfo(accessToken: String): SocialProfile {
        try {
            val headers = org.springframework.http.HttpHeaders()
            headers.set("Authorization", "Bearer $accessToken")

            val entity = org.springframework.http.HttpEntity<String>(headers)
            val response =
                restTemplate.exchange(
                    KAKAO_USER_INFO_URL,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    Map::class.java,
                )

            val body =
                response.body
                    ?: throw AuthException(
                        AuthErrorCode.EXTERNAL_API_ERROR,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Failed to fetch Kakao user info",
                    )

            val providerUserId =
                body["id"]?.toString()
                    ?: throw AuthException(
                        AuthErrorCode.VALIDATION_ERROR,
                        HttpStatus.BAD_REQUEST,
                        "No user ID in Kakao response",
                    )

            val account = body["kakao_account"] as? Map<*, *>
            val profile = account?.get("profile") as? Map<*, *>

            return SocialProfile(
                provider = OAuth2Provider.KAKAO.registrationId,
                providerUserId = providerUserId,
                email = account?.get("email") as? String,
                profileImageUrl = profile?.get("profile_image_url") as? String,
            )
        } catch (e: RestClientException) {
            throw AuthException(
                AuthErrorCode.EXTERNAL_API_ERROR,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Failed to fetch Kakao user info: ${e.message}",
                e,
            )
        }
    }
}
