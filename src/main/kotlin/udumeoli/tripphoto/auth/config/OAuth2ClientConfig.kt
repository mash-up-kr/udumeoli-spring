@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package udumeoli.tripphoto.auth.config

import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository

@Configuration
@EnableConfigurationProperties(OAuth2ClientProperties::class)
class OAuth2ClientConfig {
    @Bean
    fun clientRegistrationRepository(properties: OAuth2ClientProperties): ClientRegistrationRepository {
        val registrations =
            properties.registration
                .entries
                .filter { (_, registration) ->
                    // client-id가 비어있으면 해당 Provider 비활성화
                    !registration.clientId.isNullOrBlank()
                }.mapNotNull { (registrationId, registration) ->
                    try {
                        val provider = properties.provider[registrationId]
                        buildClientRegistration(registrationId, registration, provider)
                    } catch (e: Exception) {
                        // 잘못된 설정은 무시하고 로그만 출력
                        println("⚠️  OAuth2 Provider '$registrationId' 설정 오류: ${e.message}")
                        null
                    }
                }

        if (registrations.isEmpty()) {
            throw IllegalStateException(
                "최소 하나 이상의 OAuth2 Provider가 설정되어야 합니다. " +
                    "KAKAO_CLIENT_ID, GOOGLE_CLIENT_ID, APPLE_CLIENT_ID 중 하나 이상을 환경변수에 설정하세요.",
            )
        }

        println("✅ 활성화된 OAuth2 Providers: ${registrations.map { it.registrationId }}")
        return InMemoryClientRegistrationRepository(registrations)
    }

    private fun buildClientRegistration(
        registrationId: String,
        registration: OAuth2ClientProperties.Registration,
        provider: OAuth2ClientProperties.Provider?,
    ): ClientRegistration {
        val builder = ClientRegistration.withRegistrationId(registrationId)

        builder.clientId(registration.clientId)
        builder.clientSecret(registration.clientSecret)
        builder.clientAuthenticationMethod(
            org.springframework.security.oauth2.core.ClientAuthenticationMethod(
                registration.clientAuthenticationMethod ?: "client_secret_basic",
            ),
        )
        builder.authorizationGrantType(
            org.springframework.security.oauth2.core.AuthorizationGrantType(
                registration.authorizationGrantType ?: "authorization_code",
            ),
        )
        builder.redirectUri(registration.redirectUri ?: "{baseUrl}/login/oauth2/code/{registrationId}")
        builder.scope(registration.scope ?: emptySet())
        builder.clientName(registration.clientName ?: registrationId)

        if (provider != null) {
            builder.authorizationUri(provider.authorizationUri)
            builder.tokenUri(provider.tokenUri)
            builder.userInfoUri(provider.userInfoUri)
            builder.userNameAttributeName(provider.userNameAttribute)
            provider.jwkSetUri?.let { builder.jwkSetUri(it) }
        }

        return builder.build()
    }
}
