package udumeoli.tripphoto.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("storage")
data class StorageProperties(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val publicBaseUrl: String,
    val upload: UploadPolicy = UploadPolicy(),
) {
    data class UploadPolicy(
        val urlTtl: Duration = Duration.ofMinutes(DEFAULT_URL_TTL_MINUTES),
        val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
    )

    companion object {
        private const val DEFAULT_URL_TTL_MINUTES = 5L
        private const val DEFAULT_MAX_SIZE_BYTES = 20L * 1024 * 1024
    }
}
