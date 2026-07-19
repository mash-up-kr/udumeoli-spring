package udumeoli.tripphoto.image.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.ReadOnlyProperty
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
     * @ReadOnlyProperty: 백엔드가 발행하는 INSERT/UPDATE에서 이 컬럼을 제외한다.
     * 없으면 copy()+save() 코드가 서버가 채운 값을 stale 값(null)으로 되돌리는 타이밍 버그가 가능
     * — 주석 경고 대신 구조적으로 차단 (SELECT에서는 정상적으로 읽힌다).
     */
    @ReadOnlyProperty
    @Column("thumbnail_url")
    val thumbnailUrl: String? = null,
    @Column("uploader_id")
    val uploaderId: Long? = null,
    @CreatedDate
    @Column("created_at")
    val createdAt: LocalDateTime? = null,
)
