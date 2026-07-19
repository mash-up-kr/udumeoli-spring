package udumeoli.tripphoto.image.thumbnail

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("thumbnail")
data class ThumbnailProperties(
    /** 썸네일 생성 서버(Go)의 base URL. 빈 값이면 생성 요청을 건너뛴다 (로컬 개발·테스트). */
    val serverUrl: String = "",
)
