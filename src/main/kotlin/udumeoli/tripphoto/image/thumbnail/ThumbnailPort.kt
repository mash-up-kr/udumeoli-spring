package udumeoli.tripphoto.image.thumbnail

/**
 * 썸네일 생성 서버(Go, POST /thumbnail) 연동 경계.
 *
 * 실제 서버의 동작 방식에 맞춘 계약이다:
 * - 요청은 접수만 되고(202) 백그라운드 워커가 순차 처리한다 — fire-and-forget.
 * - 완료 콜백이 없다. 서버가 image.thumbnail_url을 **DB에 직접 UPDATE**한다.
 *   따라서 백엔드에는 완료를 수신하는 코드가 존재하지 않는다.
 * - 어느 단계든 실패하면 재시도 없이 thumbnail_url이 null로 남고, 프론트가 원본으로 폴백한다.
 * - imageUrl은 서버가 인증 없이 HTTP GET으로 내려받는다 → 원본이 공개 접근 가능해야 한다 (D2 전제).
 */
interface ThumbnailPort {
    fun requestThumbnail(
        imageId: Long,
        imageUrl: String,
    )
}
