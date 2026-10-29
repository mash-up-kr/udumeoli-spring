package udumeoli.tripphoto.config.remote

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.KeysAndValues
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Date

class RemoteConfigSdkTest {
    @Test
    fun `SDK 템플릿 비율 조건은 사용자별로 일관되게 평가된다`() {
        val options =
            FirebaseOptions
                .builder()
                .setProjectId("test-project")
                .setCredentials(GoogleCredentials.create(AccessToken("offline-test", Date(Long.MAX_VALUE))))
                .setHttpTransport(NetHttpTransport())
                .build()
        val app = FirebaseApp.initializeApp(options, "remote-config-offline-test")
        try {
            val template =
                FirebaseRemoteConfig
                    .getInstance(app)
                    .serverTemplateBuilder()
                    .defaultConfig(KeysAndValues.Builder().build())
                    .cachedTemplate(
                        """
                        {
                          "conditions": [{"name": "treatment", "condition": {"percent": {
                            "seed": "experiment-v1", "percentOperator": "LESS_OR_EQUAL", "microPercent": 50000000
                          }}}],
                          "parameters": {"variant": {
                            "defaultValue": {"value": "control"},
                            "conditionalValues": {"treatment": {"value": "treatment"}}
                          }}
                        }
                        """.trimIndent(),
                    ).build()
            val variants =
                (1..100).map { id ->
                    val context = KeysAndValues.Builder().put("randomizationId", "user-$id").build()
                    val value = template.evaluate(context).getString("variant")
                    assertThat(template.evaluate(context).getString("variant")).isEqualTo(value)
                    value
                }
            assertThat(variants).contains("control", "treatment")
        } finally {
            app.delete()
        }
    }
}
