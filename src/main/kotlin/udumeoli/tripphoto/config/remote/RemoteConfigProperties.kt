package udumeoli.tripphoto.config.remote

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("firebase.remote-config")
data class RemoteConfigProperties(
    val enabled: Boolean = false,
    val projectId: String = "",
    val credentialsPath: String = "",
    val refreshInterval: Duration = Duration.parse("PT5M"),
)
