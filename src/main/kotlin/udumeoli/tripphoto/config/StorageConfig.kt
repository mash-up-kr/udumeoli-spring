package udumeoli.tripphoto.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/**
 * S3 호환 API 클라이언트 구성. OCI S3 호환 엔드포인트와 MinIO(로컬/테스트)는 둘 다
 * path-style 주소(https://endpoint/bucket/key)를 쓰므로 pathStyle을 강제한다.
 */
@Configuration
class StorageConfig {
    /** HEAD/DELETE 등 동기 호출용. presign(서명)만 필요한 경로는 [s3Presigner]를 쓴다. */
    @Bean
    fun s3Client(properties: StorageProperties): S3Client =
        S3Client
            .builder()
            .endpointOverride(URI.create(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(staticCredentials(properties))
            .forcePathStyle(true)
            .httpClient(UrlConnectionHttpClient.create())
            .build()

    /** presigned URL 생성은 순수 로컬 서명 연산 — 네트워크 호출이 발생하지 않는다. */
    @Bean
    fun s3Presigner(properties: StorageProperties): S3Presigner =
        S3Presigner
            .builder()
            .endpointOverride(URI.create(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(staticCredentials(properties))
            .serviceConfiguration(
                S3Configuration
                    .builder()
                    .pathStyleAccessEnabled(true)
                    .build(),
            ).build()

    private fun staticCredentials(properties: StorageProperties): StaticCredentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
        )
}
