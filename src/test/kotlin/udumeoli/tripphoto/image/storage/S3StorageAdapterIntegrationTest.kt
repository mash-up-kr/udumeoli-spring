package udumeoli.tripphoto.image.storage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import udumeoli.tripphoto.config.StorageConfig
import udumeoli.tripphoto.config.StorageProperties
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

/**
 * S3 호환 스토리지(MinIO)에 대한 어댑터 왕복 검증: presign → PUT → HEAD → DELETE.
 * OCI Object Storage도 동일한 S3 호환 API로 붙는다는 전제를 검증하는 테스트.
 * Docker 필요 — `./gradlew integrationTest`로 실행 (CI는 pr.yml).
 */
@Tag("integration")
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3StorageAdapterIntegrationTest {
    private lateinit var s3Client: S3Client
    private lateinit var adapter: S3StorageAdapter

    @BeforeAll
    fun setUp() {
        val properties =
            StorageProperties(
                endpoint = minio.s3URL,
                region = "us-east-1",
                bucket = "photato-test",
                accessKey = minio.userName,
                secretKey = minio.password,
                publicBaseUrl = "${minio.s3URL}/photato-test",
            )
        val config = StorageConfig()
        s3Client = config.s3Client(properties)
        adapter = S3StorageAdapter(s3Client, config.s3Presigner(properties), properties)
        s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket).build())
    }

    @Test
    fun `presigned URL로 PUT 업로드하면 head로 확인된다`() {
        val key = randomKey()
        val uploadUrl = adapter.createUploadUrl(key, "image/jpeg")
        val body = ByteArray(1024) { it.toByte() }

        assertThat(put(uploadUrl, "image/jpeg", body)).isEqualTo(200)

        val meta = adapter.head(key)
        assertThat(meta).isNotNull
        assertThat(meta!!.contentLength).isEqualTo(1024L)
        assertThat(meta.contentType).isEqualTo("image/jpeg")
    }

    @Test
    fun `서명과 다른 contentType으로 PUT하면 거부된다 (contentType이 서명에 묶여 있음)`() {
        val key = randomKey()
        val uploadUrl = adapter.createUploadUrl(key, "image/jpeg")

        assertThat(put(uploadUrl, "image/png", ByteArray(16))).isEqualTo(403)
        assertThat(adapter.head(key)).isNull()
    }

    @Test
    fun `업로드 전에는 head가 null이다 (IMAGE_NOT_UPLOADED 판정 근거)`() {
        assertThat(adapter.head(randomKey())).isNull()
    }

    @Test
    fun `delete는 없는 키가 섞여 있어도 성공한다 (고아 정리 재시도의 멱등성)`() {
        val key = randomKey()
        put(adapter.createUploadUrl(key, "image/webp"), "image/webp", ByteArray(16))

        adapter.delete(listOf(key, "original/ghost.webp"))

        assertThat(adapter.head(key)).isNull()
    }

    private fun randomKey() = "original/${UUID.randomUUID()}.jpg"

    private fun put(
        url: String,
        contentType: String,
        body: ByteArray,
    ): Int {
        val request =
            HttpRequest
                .newBuilder(URI.create(url))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    companion object {
        @Container
        @JvmStatic
        private val minio = MinIOContainer("minio/minio:latest")

        private val httpClient: HttpClient = HttpClient.newHttpClient()
    }
}
