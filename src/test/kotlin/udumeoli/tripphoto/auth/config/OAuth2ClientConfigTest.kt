package udumeoli.tripphoto.auth.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties

class OAuth2ClientConfigTest {
    private val config = OAuth2ClientConfig()

    @Test
    fun `YAML의 provider 블록 없이도 Google, Kakao, Apple Provider가 모두 정상 등록된다`() {
        val properties =
            OAuth2ClientProperties().apply {
                registration["google"] =
                    OAuth2ClientProperties.Registration().apply {
                        clientId = "google-client-id"
                        clientSecret = "google-client-secret"
                    }
                registration["kakao"] =
                    OAuth2ClientProperties.Registration().apply {
                        clientId = "kakao-client-id"
                        clientSecret = "kakao-client-secret"
                    }
                registration["apple"] =
                    OAuth2ClientProperties.Registration().apply {
                        clientId = "apple-client-id"
                        clientSecret = "apple-client-secret"
                    }
            }

        val repository = config.clientRegistrationRepository(properties)

        val google = repository.findByRegistrationId("google")
        assertThat(google).isNotNull
        assertThat(google?.providerDetails?.authorizationUri).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth")
        assertThat(google?.clientName).isEqualTo("Google")

        val kakao = repository.findByRegistrationId("kakao")
        assertThat(kakao).isNotNull
        assertThat(kakao?.providerDetails?.authorizationUri).isEqualTo("https://kauth.kakao.com/oauth/authorize")
        assertThat(kakao?.providerDetails?.tokenUri).isEqualTo("https://kauth.kakao.com/oauth/token")
        assertThat(kakao?.providerDetails?.userInfoEndpoint?.uri).isEqualTo("https://kapi.kakao.com/v2/user/me")
        assertThat(kakao?.clientName).isEqualTo("Kakao")

        val apple = repository.findByRegistrationId("apple")
        assertThat(apple).isNotNull
        assertThat(apple?.providerDetails?.authorizationUri)
            .isEqualTo("https://appleid.apple.com/auth/authorize?response_mode=form_post")
        assertThat(apple?.providerDetails?.tokenUri).isEqualTo("https://appleid.apple.com/auth/token")
        assertThat(apple?.providerDetails?.jwkSetUri).isEqualTo("https://appleid.apple.com/auth/keys")
        assertThat(apple?.clientName).isEqualTo("Apple")
        assertThat(apple?.scopes).containsExactlyInAnyOrder("openid", "name", "email")
    }

    @Test
    fun `YAML에 provider 엔드포인트가 명시되어 있다면 해당 값으로 오버라이드된다`() {
        val properties =
            OAuth2ClientProperties().apply {
                registration["kakao"] =
                    OAuth2ClientProperties.Registration().apply {
                        clientId = "kakao-client-id"
                        clientSecret = "kakao-client-secret"
                    }
                provider["kakao"] =
                    OAuth2ClientProperties.Provider().apply {
                        authorizationUri = "https://custom-kakao.example.com/oauth/authorize"
                    }
            }

        val repository = config.clientRegistrationRepository(properties)

        val kakao = repository.findByRegistrationId("kakao")
        assertThat(kakao).isNotNull
        val expectedUri = "https://custom-kakao.example.com/oauth/authorize"
        assertThat(kakao?.providerDetails?.authorizationUri).isEqualTo(expectedUri)
    }

    @Test
    fun `client-id가 비어있는 Provider는 비활성화된다`() {
        val properties =
            OAuth2ClientProperties().apply {
                registration["google"] =
                    OAuth2ClientProperties.Registration().apply {
                        clientId = ""
                    }
                registration["kakao"] =
                    OAuth2ClientProperties.Registration().apply {
                        clientId = "kakao-client-id"
                        clientSecret = "kakao-client-secret"
                    }
            }

        val repository = config.clientRegistrationRepository(properties)

        assertThat(repository.findByRegistrationId("google")).isNull()
        assertThat(repository.findByRegistrationId("kakao")).isNotNull
    }

    @Test
    fun `모든 Provider의 client-id가 비어있으면 예외가 발생한다`() {
        val properties =
            OAuth2ClientProperties().apply {
                registration["google"] =
                    OAuth2ClientProperties.Registration().apply {
                        clientId = ""
                    }
            }

        assertThatThrownBy { config.clientRegistrationRepository(properties) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("최소 하나 이상의 OAuth2 Provider가 설정되어야 합니다.")
    }
}
