package udumeoli.tripphoto.image.thumbnail

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("thumbnail")
data class ThumbnailProperties(
    /** 콜백 인증용 공유 시크릿. 빈 값이면 콜백을 전부 거부한다 (미설정 = 잠금, 열림 아님). */
    val callbackToken: String = "",
)
