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
