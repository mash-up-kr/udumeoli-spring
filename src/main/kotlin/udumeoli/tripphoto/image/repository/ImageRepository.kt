package udumeoli.tripphoto.image.repository

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.query.Param
import udumeoli.tripphoto.image.entity.Image
import java.time.LocalDateTime

interface ImageRepository : ListCrudRepository<Image, Long> {
    fun findAllByUploaderId(uploaderId: Long): List<Image>

    /**
     * 고아 이미지: 어떤 여행에도 연결되지 않은 채 threshold 이전에 생성된 행.
     * "URL만 발급하고 업로드 안 함"과 "업로드했지만 createTrip 안 함" 둘 다 잡힌다.
     * 현재 호출처 없음 — 자동 정리 배치는 MVP 규모에서 의도적으로 보류했고, 고아는 시간이 지나도
     * 이 조건으로 판별/회수 가능하므로 그 근거로 쿼리를 남겨둔다 (계획 문서 8.3 '보류 결정').
     */
    @Query(
        """
        SELECT * FROM image i
        WHERE i.created_at < :threshold
          AND NOT EXISTS (SELECT 1 FROM trip_image ti WHERE ti.image_id = i.id)
        """,
    )
    fun findOrphansCreatedBefore(
        @Param("threshold") threshold: LocalDateTime,
    ): List<Image>
}
