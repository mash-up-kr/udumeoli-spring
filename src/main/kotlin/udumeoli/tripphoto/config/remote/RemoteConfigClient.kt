package udumeoli.tripphoto.config.remote

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.auth.http.HttpTransportFactory
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.KeysAndValues
import com.google.firebase.remoteconfig.ServerConfig
import com.google.firebase.remoteconfig.ServerTemplate
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

@Component
class RemoteConfigClient(
    private val properties: RemoteConfigProperties,
) {
    private var app: FirebaseApp? = null
    private var template: ServerTemplate? = null

    @Synchronized
    fun refresh() {
        val current = template ?: createTemplate().also { template = it }
        current.load().get()
    }

    @Synchronized
    fun evaluate(
        userId: String,
        signals: Map<String, String>,
    ): ServerConfig? {
        val context = KeysAndValues.Builder()
        signals.filterKeys { it != RANDOMIZATION_ID_KEY }.forEach { (key, value) -> context.put(key, value) }
        context.put(RANDOMIZATION_ID_KEY, userId)
        return template?.evaluate(context.build())
    }

    private fun createTemplate(): ServerTemplate {
        val transport = NetHttpTransport()
        val credentials =
            Files.newInputStream(Path.of(properties.credentialsPath)).use {
                ServiceAccountCredentials.fromStream(it, HttpTransportFactory { transport })
            }
        val options =
            FirebaseOptions
                .builder()
                .setProjectId(properties.projectId)
                .setCredentials(credentials)
                .setHttpTransport(transport)
                .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                .setReadTimeout(READ_TIMEOUT_MILLIS)
                .build()
        val firebaseApp = app ?: FirebaseApp.initializeApp(options, "remote-config").also { app = it }
        return FirebaseRemoteConfig
            .getInstance(firebaseApp)
            .serverTemplateBuilder()
            .defaultConfig(KeysAndValues.Builder().build())
            .cachedTemplate("{}")
            .build()
    }

    @PreDestroy
    @Synchronized
    fun close() {
        app?.delete()
        app = null
        template = null
    }

    companion object {
        // SDK가 비율 조건 평가에 사용하는 고정 키. 값에는 사용자별로 일관된 ID를 전달한다.
        // custom signals가 이 값을 덮어쓰지 못하도록 필터링하고, 전달받은 userId를 사용한다.
        private const val RANDOMIZATION_ID_KEY = "randomizationId"
        private const val CONNECT_TIMEOUT_MILLIS = 2000
        private const val READ_TIMEOUT_MILLIS = 5000
    }
}
