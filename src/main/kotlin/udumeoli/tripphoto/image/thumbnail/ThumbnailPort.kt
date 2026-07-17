package udumeoli.tripphoto.image.thumbnail

/**
 * 썸네일 생성 서버 연동 경계.
 * 서버 형태(사이드카/OCI Functions 등)가 협의 미확정이라 no-op 구현으로 시작한다 — 계약상
 * thumbnailUrl이 null이면 프론트가 원본으로 폴백하므로 썸네일 없이도 파이프라인은 완결된다.
 * 생성 완료는 [ThumbnailCallbackController] 콜백으로 수신한다.
 */
interface ThumbnailPort {
    fun requestThumbnail(
        imageId: Long,
        objectKey: String,
    )
}
