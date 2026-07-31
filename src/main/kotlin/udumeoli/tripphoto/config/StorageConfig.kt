package udumeoli.tripphoto.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/**
 * Object Storage 접속 객체를 만든다.
 *
 * 서버는 이미지 바이트를 직접 받지 않고 "이 URL로 직접 올려라"는 presigned URL만 발급하므로
 * 필요한 건 서명기(S3Presigner) 하나뿐이다. (업로드/조회는 클라이언트가 그 URL로 직접 한다)
 */
@Configuration
class StorageConfig {
    /**
     * 업로드용 presigned URL 서명기.
     * 여기 담긴 자격증명으로 URL에 서명하고, 스토리지는 그 서명만 보고 익명 PUT을 허용한다.
     */
    @Bean
    fun s3Presigner(properties: StorageProperties): S3Presigner =
        S3Presigner
            .builder()
            // AWS가 아니라 OCI/MinIO로 붙기 위해 SDK 기본 엔드포인트를 덮어쓴다
            .endpointOverride(URI.create(properties.endpoint))
            // 실제 라우팅에는 안 쓰이고 SigV4 서명 문자열에 들어가는 값 — 스토리지 쪽 설정과 같아야 한다
            .region(Region.of(properties.region))
            .credentialsProvider(staticCredentials(properties))
            .serviceConfiguration(
                S3Configuration
                    .builder()
                    // 버킷을 호스트에 붙이는 가상 호스팅 방식(bucket.host/key) 대신 host/bucket/key 방식.
                    // MinIO/OCI는 버킷별 DNS가 없어서 이걸 꺼두면 주소를 못 찾는다.
                    .pathStyleAccessEnabled(true)
                    .build(),
            ).build()

    /**
     * IAM 롤/인스턴스 프로파일 같은 AWS 전용 자격증명 탐색을 쓰지 않고,
     * yml로 주입한 키를 고정으로 사용한다 (OCI/MinIO에는 그 탐색 경로가 없다).
     */
    private fun staticCredentials(properties: StorageProperties): StaticCredentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
        )
}
