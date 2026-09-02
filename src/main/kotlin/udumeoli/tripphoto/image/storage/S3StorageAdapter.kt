package udumeoli.tripphoto.image.storage

import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import udumeoli.tripphoto.config.StorageProperties

@Component
class S3StorageAdapter(
    private val s3Presigner: S3Presigner,
    private val properties: StorageProperties,
) {
    fun createUploadUrl(
        objectKey: String,
        contentType: String,
        dek: ByteArray,
    ): String {
        val ssec =
            udumeoli.tripphoto.image.dto.SsecHeader
                .fromDek(dek)

        val putRequest: software.amazon.awssdk.services.s3.model.PutObjectRequest =
            PutObjectRequest
                .builder()
                .bucket(properties.bucket)
                .key(objectKey)
                .contentType(contentType)
                .sseCustomerAlgorithm(ssec.algorithm)
                .sseCustomerKey(ssec.keyBase64)
                .sseCustomerKeyMD5(ssec.keyMd5Base64)
                .build()
        val presignRequest: software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest =
            PutObjectPresignRequest
                .builder()
                .signatureDuration(properties.uploadUrlTtl)
                .putObjectRequest(putRequest)
                .build()
        return s3Presigner.presignPutObject(presignRequest).url().toString()
    }

    fun publicUrl(objectKey: String): String = "${properties.publicBaseUrl.trimEnd('/')}/$objectKey"
}
