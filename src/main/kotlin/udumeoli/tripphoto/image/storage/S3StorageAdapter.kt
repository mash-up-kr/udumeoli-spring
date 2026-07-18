package udumeoli.tripphoto.image.storage

import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import udumeoli.tripphoto.config.StorageProperties

@Component
class S3StorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: StorageProperties,
) : ImageStoragePort {
    override fun createUploadUrl(
        objectKey: String,
        contentType: String,
    ): String {
        // contentType을 서명에 포함 — 다른 Content-Type으로 PUT하면 스토리지가 403으로 거부한다
        val putRequest =
            PutObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(objectKey)
                .contentType(contentType)
                .build()
        val presignRequest =
            PutObjectPresignRequest
                .builder()
                .signatureDuration(properties.upload.urlTtl)
                .putObjectRequest(putRequest)
                .build()
        return s3Presigner.presignPutObject(presignRequest).url().toString()
    }

    override fun head(objectKey: String): StoredObjectMeta? =
        try {
            val response =
                s3Client.headObject(
                    HeadObjectRequest
                        .builder()
                        .bucket(properties.bucket)
                        .key(objectKey)
                        .build(),
                )
            StoredObjectMeta(
                contentLength = response.contentLength(),
                contentType = response.contentType(),
            )
        } catch (ignored: NoSuchKeyException) {
            null
        }

    override fun delete(objectKeys: Collection<String>) {
        if (objectKeys.isEmpty()) {
            return
        }
        val identifiers = objectKeys.map { ObjectIdentifier.builder().key(it).build() }
        val response =
            s3Client.deleteObjects(
                DeleteObjectsRequest
                    .builder()
                    .bucket(properties.bucket)
                    .delete(Delete.builder().objects(identifiers).build())
                    .build(),
            )
        // 복수 삭제는 키별 실패를 예외가 아니라 200 응답 본문(errors)으로 돌려준다.
        // 여기서 던지지 않으면 호출자(deleteImages)가 행까지 지워 객체 키의 유일한 기록이 사라진다
        // — "객체 → 행" 삭제 순서 계약은 실패 시 행이 남는다는 전제 위에 있다.
        // (없는 키 삭제는 S3 규약상 성공으로 집계되므로 멱등성 계약과 충돌하지 않는다)
        check(response.errors().isEmpty()) {
            "객체 삭제 부분 실패: ${response.errors().joinToString { "${it.key()}(${it.code()})" }}"
        }
    }

    override fun publicUrl(objectKey: String): String = "${properties.publicBaseUrl.trimEnd('/')}/$objectKey"
}
