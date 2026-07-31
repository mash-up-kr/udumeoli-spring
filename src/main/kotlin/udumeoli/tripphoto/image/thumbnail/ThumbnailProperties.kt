package udumeoli.tripphoto.image.thumbnail

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("thumbnail")
data class ThumbnailProperties(
    val serverUrl: String = "",
)
