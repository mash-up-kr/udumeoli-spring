package udumeoli.tripphoto.image.thumbnail

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** 썸네일 서버 확정 전 임시 구현 — 요청을 로그로만 남긴다. */
@Component
class NoopThumbnailAdapter : ThumbnailPort {
    override fun requestThumbnail(
        imageId: Long,
        objectKey: String,
    ) {
        log.info("썸네일 생성 요청 스킵(no-op): imageId={}, objectKey={}", imageId, objectKey)
    }

    companion object {
        private val log = LoggerFactory.getLogger(NoopThumbnailAdapter::class.java)
    }
}
