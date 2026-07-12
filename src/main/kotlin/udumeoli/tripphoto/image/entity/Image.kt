package udumeoli.tripphoto.image.entity

import org.springframework.data.annotation.CreatedDate
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
    val thumbnailUrl: String? = null,
    @Column("uploader_id")
    val uploaderId: Long? = null,
    @CreatedDate
    @Column("created_at")
    val createdAt: LocalDateTime? = null,
)
