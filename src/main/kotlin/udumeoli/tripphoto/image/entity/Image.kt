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
    /**
     * 썸네일 서버(Go)가 생성 완료 시 DB에 **직접 UPDATE**하는 컬럼 — 백엔드는 읽기만 한다.
     * 주의: 백엔드에서 이 엔티티를 copy()로 수정·save()하면 그 사이 서버가 채운 값을
     * 과거 값(null)으로 덮어쓸 수 있다. image 행을 UPDATE하는 코드를 새로 만들 때 유의.
     */
    @Column("thumbnail_url")
    val thumbnailUrl: String? = null,
    @Column("uploader_id")
    val uploaderId: Long? = null,
    @CreatedDate
    @Column("created_at")
    val createdAt: LocalDateTime? = null,
)
