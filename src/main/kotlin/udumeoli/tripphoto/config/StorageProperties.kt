package udumeoli.tripphoto.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Object Storage 접속 정보 (OCI Object Storage의 S3 호환 API).
 * 값은 env로 주입된다. base(application.properties)의 로컬 MinIO 폴백은 개발/테스트 편의용이고,
 * 운영(dev 프로파일)은 application-dev.properties가 기본값 없는 placeholder로 env를 필수 강제한다
 * — presign이 로컬 서명이라 폴백 값으로도 "성공"해버리는 조용한 오동작을 부팅 실패로 바꾸기 위함.
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
