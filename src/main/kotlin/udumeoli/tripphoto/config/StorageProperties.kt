package udumeoli.tripphoto.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 이미지 원본을 보관하는 Object Storage 접속 정보.
 *
 * 운영은 OCI Object Storage, 로컬은 MinIO를 쓰는데 둘 다 S3 호환 API라
 * "어느 엔드포인트에 어떤 키로 붙을지"만 환경별로 갈아끼우면 된다.
 * 값은 application.yml의 `storage.*` 에서 주입된다 ([StorageConfig]가 이걸로 S3 클라이언트를 만든다).
 */
@ConfigurationProperties("storage")
data class StorageProperties(
    /** S3 호환 API 주소. 로컬 MinIO는 http://localhost:9000, 운영은 OCI 네임스페이스 엔드포인트. */
    val endpoint: String,
    /** AWS 리전 이름. OCI/MinIO는 실제 리전 개념이 없지만 SDK가 서명(SigV4)에 쓰므로 필수다. */
    val region: String,
    /** 원본 이미지를 넣을 버킷 이름. */
    val bucket: String,
    /** 서명용 access key. */
    val accessKey: String,
    /** 서명용 secret key. */
    val secretKey: String,
    /**
     * 업로드된 객체를 공개로 읽을 때의 base URL. `{publicBaseUrl}/{objectKey}` 가 Image.originalUrl이 된다.
     * 업로드용 presigned URL과 달리 만료가 없어서 DB에 그대로 저장할 수 있다.
     */
    val publicBaseUrl: String,
    /**
     * 발급한 업로드용 presigned URL의 유효 기간.
     * 짧을수록 URL 유출 위험이 줄고, 길수록 느린 네트워크에서 업로드 실패가 줄어든다.
     */
    val uploadUrlTtl: Duration = Duration.ofMinutes(DEFAULT_UPLOAD_URL_TTL_MINUTES),
) {
    companion object {
        private const val DEFAULT_UPLOAD_URL_TTL_MINUTES = 5L
    }
}
