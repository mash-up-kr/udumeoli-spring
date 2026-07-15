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
    /** 스토리지 객체 키 (예: original/{uuid}.jpg). 업로드 검증(HEAD)과 삭제가 이 키로 동작한다. */
    @Column("object_key")
    val objectKey: String,
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
