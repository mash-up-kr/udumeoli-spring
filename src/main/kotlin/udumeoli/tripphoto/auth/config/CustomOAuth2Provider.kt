package udumeoli.tripphoto.auth.config

import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod

/**
 * Spring Security의 [org.springframework.security.config.oauth2.client.CommonOAuth2Provider]와 동일한 방식으로,
 * 서드파티 커스텀 Provider(Kakao, Apple)의 엔드포인트 및 기본 설정을 코드 레벨에서 사전 정의한 enum.
 *
 * application.yml에 별도의 provider 블록을 작성하지 않아도 registration 설정만으로 동작합니다.
 */
enum class CustomOAuth2Provider {
    KAKAO {
        override fun getBuilder(registrationId: String): ClientRegistration.Builder =
            ClientRegistration
                .withRegistrationId(registrationId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(DEFAULT_REDIRECT_URL)
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .clientName("Kakao")
    },
    APPLE {
        override fun getBuilder(registrationId: String): ClientRegistration.Builder =
            ClientRegistration
                .withRegistrationId(registrationId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(DEFAULT_REDIRECT_URL)
                .scope("openid", "name", "email")
                .authorizationUri("https://appleid.apple.com/auth/authorize?response_mode=form_post")
                .tokenUri("https://appleid.apple.com/auth/token")
                // Apple은 UserInfo 엔드포인트를 제공하지 않는다. 사용자 정보(sub, email)는 id_token claim에서 읽는다.
                .jwkSetUri("https://appleid.apple.com/auth/keys")
                .userNameAttributeName("sub")
                .clientName("Apple")
    }, ;

    abstract fun getBuilder(registrationId: String): ClientRegistration.Builder

    companion object {
        private const val DEFAULT_REDIRECT_URL = "{baseUrl}/login/oauth2/code/{registrationId}"

        fun fromRegistrationId(registrationId: String): CustomOAuth2Provider? =
            entries.find { it.name.equals(registrationId, ignoreCase = true) }
    }
}
