package udumeoli.tripphoto.image.storage

/**
 * Object Storage 경계 추상화.
 * 스토리지가 "업로드 여부"의 single source of truth다 — DB에 상태 컬럼을 두지 않고
 * 필요한 시점에 [head]로 직접 확인한다 (V2에서 image.status 제거한 결정의 연장).
 */
interface ImageStoragePort {
    /** objectKey에 PUT 업로드할 수 있는 presigned URL을 발급한다. */
    fun createUploadUrl(
        objectKey: String,
        contentType: String,
    ): String

    /** 객체 메타데이터 조회. 객체가 없으면(= 미업로드) null. */
    fun head(objectKey: String): StoredObjectMeta?

    /** 객체 일괄 삭제. 존재하지 않는 키가 섞여 있어도 성공해야 한다 (고아 정리 재시도의 멱등성). */
    fun delete(objectKeys: Collection<String>)

    /** 조회용 공개 URL. */
    fun publicUrl(objectKey: String): String
}

data class StoredObjectMeta(
    val contentLength: Long,
    val contentType: String?,
)
