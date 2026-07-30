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
) {
    fun createUploadUrl(
        objectKey: String,
        contentType: String,
    ): String {
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
        check(response.errors().isEmpty()) {
            "객체 삭제 부분 실패: ${response.errors().joinToString { "${it.key()}(${it.code()})" }}"
        }
    }

    fun publicUrl(objectKey: String): String = "${properties.publicBaseUrl.trimEnd('/')}/$objectKey"
}

data class StoredObjectMeta(
    val contentLength: Long,
    val contentType: String?,
)
