package udumeoli.tripphoto.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Object Storage 접속 정보 (OCI Object Storage의 S3 호환 API).
 * 값은 application.properties에서 env로 주입되며, env 미설정 시 로컬 MinIO 기본값으로 폴백한다
 * — 스토리지 env가 빠져도 앱 부팅과 나머지 기능은 살아 있게 하기 위함 (스토리지 호출 시점에만 실패).
 */
@ConfigurationProperties("storage")
data class StorageProperties(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    /** 조회용 공개 URL prefix. originalUrl = "{publicBaseUrl}/{objectKey}" */
    val publicBaseUrl: String,
    val upload: UploadPolicy = UploadPolicy(),
) {
    data class UploadPolicy(
        /** presigned PUT URL 만료 시간 (협의 포인트 — 기본 5분) */
        val urlTtl: Duration = Duration.ofMinutes(DEFAULT_URL_TTL_MINUTES),
        /**
         * 최대 업로드 크기. presigned PUT은 크기를 사전에 강제할 수 없어
         * createTrip 검증 시점에 HEAD Content-Length로 사후 검증한다 (계획 D8).
         */
        val maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
    )

    companion object {
        private const val DEFAULT_URL_TTL_MINUTES = 5L
        private const val DEFAULT_MAX_SIZE_BYTES = 10L * 1024 * 1024
    }
}
