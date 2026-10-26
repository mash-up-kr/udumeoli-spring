@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package udumeoli.tripphoto.auth.config

import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod

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
                        buildClientRegistration(registrationId, registration, properties.provider[registrationId])
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
        providerProperties: OAuth2ClientProperties.Provider?,
    ): ClientRegistration {
        val builder = initBuilder(registrationId, providerProperties)
        applyRegistration(builder, registration)
        applyProviderOverrides(builder, providerProperties)
        return builder.build()
    }

    private fun initBuilder(
        registrationId: String,
        providerProperties: OAuth2ClientProperties.Provider?,
    ): ClientRegistration.Builder =
        when (registrationId.lowercase()) {
            "google" -> CommonOAuth2Provider.GOOGLE.getBuilder(registrationId)
            "kakao" -> CustomOAuth2Provider.KAKAO.getBuilder(registrationId)
            "apple" -> CustomOAuth2Provider.APPLE.getBuilder(registrationId)
            else -> buildUnknownProvider(registrationId, providerProperties)
        }

    private fun buildUnknownProvider(
        registrationId: String,
        providerProperties: OAuth2ClientProperties.Provider?,
    ): ClientRegistration.Builder {
        requireNotNull(providerProperties) { "Unsupported OAuth2 provider: $registrationId" }
        return ClientRegistration
            .withRegistrationId(registrationId)
            .authorizationUri(providerProperties.authorizationUri)
            .tokenUri(providerProperties.tokenUri)
            .userInfoUri(providerProperties.userInfoUri)
            .userNameAttributeName(providerProperties.userNameAttribute)
            .apply { providerProperties.jwkSetUri?.let { jwkSetUri(it) } }
    }

    private fun applyRegistration(
        builder: ClientRegistration.Builder,
        registration: OAuth2ClientProperties.Registration,
    ) {
        builder.clientId(registration.clientId)
        registration.clientSecret?.let { builder.clientSecret(it) }
        registration.clientAuthenticationMethod?.let {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod(it))
        }
        registration.authorizationGrantType?.let {
            builder.authorizationGrantType(AuthorizationGrantType(it))
        }
        registration.redirectUri?.let { builder.redirectUri(it) }
        registration.scope?.takeIf { it.isNotEmpty() }?.let { builder.scope(it) }
        registration.clientName?.let { builder.clientName(it) }
    }

    private fun applyProviderOverrides(
        builder: ClientRegistration.Builder,
        providerProperties: OAuth2ClientProperties.Provider?,
    ) {
        if (providerProperties == null) return
        providerProperties.authorizationUri?.let { builder.authorizationUri(it) }
        providerProperties.tokenUri?.let { builder.tokenUri(it) }
        providerProperties.userInfoUri?.let { builder.userInfoUri(it) }
        providerProperties.userNameAttribute?.let { builder.userNameAttributeName(it) }
        providerProperties.jwkSetUri?.let { builder.jwkSetUri(it) }
    }
}
