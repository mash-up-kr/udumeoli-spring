package udumeoli.tripphoto.auth.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table("refresh_token")
data class RefreshToken(
    @Id
    val id: Long? = null,
    @Column("service_user_id")
    val serviceUserId: Long,
    @Column("token_hash")
    val tokenHash: String,
    @Column("expires_at")
    val expiresAt: LocalDateTime,
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
