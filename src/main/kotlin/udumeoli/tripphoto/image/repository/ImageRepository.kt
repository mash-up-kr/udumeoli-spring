package udumeoli.tripphoto.image.repository

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository
import org.springframework.data.repository.query.Param
import udumeoli.tripphoto.image.entity.Image
import java.time.LocalDateTime

interface ImageRepository : ListCrudRepository<Image, Long> {
    fun findAllByUploaderId(uploaderId: Long): List<Image>

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
