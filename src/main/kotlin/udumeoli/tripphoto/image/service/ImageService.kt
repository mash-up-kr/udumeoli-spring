package udumeoli.tripphoto.image.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import udumeoli.tripphoto.common.error.DomainException
import udumeoli.tripphoto.common.error.ErrorCode
import udumeoli.tripphoto.config.StorageProperties
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.image.storage.ImageStoragePort
import udumeoli.tripphoto.image.thumbnail.ThumbnailPort
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val storagePort: ImageStoragePort,
    private val thumbnailPort: ThumbnailPort,
    private val properties: StorageProperties,
) {
    /**
     * presigned 업로드 URL 발급 (createImageUploadUrl 뮤테이션).
     * image 행을 먼저 INSERT해 키를 확정한다 — 미사용 행은 [cleanUpOrphans]가 회수한다.
     * uploaderId는 인증(카카오 OAuth) 도입 전까지 null.
     * 인증 도입 시 uploaderId를 필수화하고 [verifyUploaded]에 요청자 == uploader 검증을 추가해야 한다 (IDOR 방지).
     */
    fun issueUploadUrl(
        contentType: String,
        uploaderId: Long? = null,
    ): ImageUploadTarget {
        val extension =
            ALLOWED_CONTENT_TYPES[contentType]
                ?: throw DomainException(
                    ErrorCode.VALIDATION_ERROR,
                    "허용되지 않는 contentType: $contentType (허용: ${ALLOWED_CONTENT_TYPES.keys.joinToString()})",
                )
        val objectKey = "original/${UUID.randomUUID()}.$extension"
        val image =
            imageRepository.save(
                Image(
                    objectKey = objectKey,
                    originalUrl = storagePort.publicUrl(objectKey),
                    uploaderId = uploaderId,
                ),
            )
        return ImageUploadTarget(
            imageId = image.id!!,
            uploadUrl = storagePort.issueUploadUrl(objectKey, contentType),
        )
    }

    /**
     * 업로드 검증 — createTrip/updateTrip이 imageIds 처리 전에 호출한다.
     * 업로드 여부는 스토리지가 source of truth라 HEAD로 직접 확인한다 (상태 컬럼 없음).
     * 반환 순서는 요청한 imageIds 순서를 따른다.
     */
    fun verifyUploaded(imageIds: List<Long>): List<Image> {
        val imagesById = imageRepository.findAllById(imageIds).associateBy { it.id!! }
        val missingIds = imageIds.filterNot(imagesById::containsKey)
        if (missingIds.isNotEmpty()) {
            throw DomainException(ErrorCode.IMAGE_NOT_FOUND, "존재하지 않는 이미지: $missingIds")
        }
        val images = imageIds.map(imagesById::getValue)
        images.forEach(::verifyStoredObject)
        return images
    }

    /**
     * 썸네일 생성 요청 — createTrip/updateTrip이 트랜잭션 커밋 후에 호출한다.
     * 썸네일은 폴백(원본 표시)이 있는 작업이라 실패해도 호출자에게 전파하지 않는다.
     * 완료는 백엔드로 돌아오지 않는다 — 썸네일 서버가 image.thumbnail_url을 DB에 직접 UPDATE한다.
     */
    fun requestThumbnails(images: List<Image>) {
        images.forEach { image ->
            runCatching { thumbnailPort.requestThumbnail(image.id!!, image.originalUrl) }
                .onFailure { log.warn("썸네일 생성 요청 실패: imageId={}", image.id, it) }
        }
    }

    /**
     * 이미지 삭제 — updateTrip "통째 교체"에서 밀려난 기존 사진 정리용.
     * 객체 → 행 순서: 중간에 실패해도 행이 남아 [cleanUpOrphans]가 재시도할 수 있다.
     * (반대 순서면 키를 아는 곳이 사라져 영구 고아가 된다)
     * DB 트랜잭션 안에서 호출하지 말 것 — 롤백돼도 스토리지 삭제는 되돌릴 수 없다 (AFTER_COMMIT에서 호출).
     */
    fun deleteImages(imageIds: Collection<Long>) {
        if (imageIds.isEmpty()) {
            return
        }
        val images = imageRepository.findAllById(imageIds)
        deleteFromStorage(images)
        imageRepository.deleteAllById(images.map { it.id!! })
    }

    /**
     * 고아 이미지 정리 — 여행에 연결되지 않은 채 [ORPHAN_RETENTION]이 지난 행과 객체를 삭제한다.
     * 삭제 순서/멱등성 계약은 [deleteImages]와 동일.
     * @return 정리한 이미지 수
     */
    fun cleanUpOrphans(): Int {
        val threshold = LocalDateTime.now().minus(ORPHAN_RETENTION)
        val orphans = imageRepository.findOrphansCreatedBefore(threshold)
        if (orphans.isEmpty()) {
            return 0
        }
        deleteFromStorage(orphans)
        imageRepository.deleteAllById(orphans.map { it.id!! })
        return orphans.size
    }

    /** 원본과 (있다면) 썸네일 객체를 함께 삭제한다. */
    private fun deleteFromStorage(images: List<Image>) {
        storagePort.delete(images.flatMap { listOfNotNull(it.objectKey, thumbnailKeyOf(it)) })
    }

    /**
     * 썸네일 객체 키 역산. 썸네일 서버는 키를 알려주지 않고 thumbnail_url만 DB에 남기므로
     * 우리 public URL prefix를 벗겨 키를 얻는다. prefix가 다른 URL(외부 출처)이면 삭제 대상에서 제외.
     */
    private fun thumbnailKeyOf(image: Image): String? {
        val thumbnailUrl = image.thumbnailUrl ?: return null
        val prefix = "${properties.publicBaseUrl.trimEnd('/')}/"
        return thumbnailUrl.removePrefix(prefix).takeIf { it != thumbnailUrl }
    }

    private fun verifyStoredObject(image: Image) {
        val meta =
            storagePort.head(image.objectKey)
                ?: throw DomainException(ErrorCode.IMAGE_NOT_UPLOADED, "원본이 업로드되지 않은 이미지: ${image.id}")
        if (meta.contentLength > properties.upload.maxSizeBytes) {
            // presigned PUT은 크기를 사전에 강제할 수 없어 여기서 사후 검증하고 초과분은 즉시 회수한다
            storagePort.delete(listOf(image.objectKey))
            throw DomainException(
                ErrorCode.VALIDATION_ERROR,
                "파일 크기 초과: ${meta.contentLength} bytes (최대 ${properties.upload.maxSizeBytes} bytes)",
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImageService::class.java)

        /** SVG는 스크립트 실행이 가능한 XSS 벡터라 의도적으로 제외 (협의 시 jpeg/png/webp 기준) */
        private val ALLOWED_CONTENT_TYPES =
            mapOf(
                "image/jpeg" to "jpg",
                "image/png" to "png",
                "image/webp" to "webp",
            )

        /** presigned 만료(5분)보다 충분히 길게 — 업로드 후 createTrip 직전의 정상 사용자를 보호 */
        private val ORPHAN_RETENTION: Duration = Duration.ofHours(24)
    }
}

/** createImageUploadUrl 응답 — schema.graphqls의 ImageUploadTarget 타입과 1:1 */
data class ImageUploadTarget(
    val imageId: Long,
    val uploadUrl: String,
)
