package udumeoli.tripphoto.common.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import java.time.LocalDateTime

data class AuditMetadata(
    @CreatedDate
    @Column("created_at")
    val createdAt: LocalDateTime? = null,
    @LastModifiedDate
    @Column("updated_at")
    val updatedAt: LocalDateTime? = null,
)
