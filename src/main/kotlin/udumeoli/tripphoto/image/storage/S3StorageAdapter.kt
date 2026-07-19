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

/**
 * Object Storage(S3 호환 API) 연동.
 * 스토리지가 "업로드 여부"의 single source of truth다 — DB에 상태 컬럼을 두지 않고
 * 필요한 시점에 [head]로 직접 확인한다 (V2에서 image.status 제거한 결정의 연장).
 */
@Component
class S3StorageAdapter(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: StorageProperties,
) {
    /** objectKey에 PUT 업로드할 수 있는 presigned URL을 발급한다. */
    fun createUploadUrl(
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

    /** 객체 메타데이터 조회. 객체가 없으면(= 미업로드) null. */
    fun head(objectKey: String): StoredObjectMeta? =
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

    /** 객체 일괄 삭제. 존재하지 않는 키가 섞여 있어도 성공해야 한다 (고아 정리 재시도의 멱등성). */
    fun delete(objectKeys: Collection<String>) {
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

    /** 조회용 공개 URL. */
    fun publicUrl(objectKey: String): String = "${properties.publicBaseUrl.trimEnd('/')}/$objectKey"
}

data class StoredObjectMeta(
    val contentLength: Long,
    val contentType: String?,
)
