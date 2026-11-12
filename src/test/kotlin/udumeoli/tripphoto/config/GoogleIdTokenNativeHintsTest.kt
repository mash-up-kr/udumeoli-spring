package udumeoli.tripphoto.config

import com.google.api.client.auth.openidconnect.IdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.json.webtoken.JsonWebToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates

class GoogleIdTokenNativeHintsTest {
    @Test
    fun `Google ID 토큰의 생성자와 상속 계층의 필드를 네이티브에 등록한다`() {
        val hints = RuntimeHints()
        ApplicationNativeHints().registerHints(hints, javaClass.classLoader)

        val reflection = RuntimeHintsPredicates.reflection()
        val payload = GoogleIdToken.Payload::class.java
        assertThat(reflection.onConstructor(payload.getConstructor()).invoke()).accepts(hints)
        assertThat(reflection.onField(payload, "email")).accepts(hints)
        assertThat(reflection.onField(IdToken.Payload::class.java, "authorizedParty")).accepts(hints)
        assertThat(reflection.onField(JsonWebToken.Payload::class.java, "subject")).accepts(hints)
    }
}
