package udumeoli.TripPhoto.image.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("image")
data class Image(
    @Id
    val id: Long? = null,
    @Column("original_url")
    val originalUrl: String,
    @Column("thumbnail_url")
    val thumbnailUrl: String,
    @Column("uploader_id")
    val uploaderId: Long,
    @Column("created_at")
    val createdAt: LocalDateTime? = null,
)
