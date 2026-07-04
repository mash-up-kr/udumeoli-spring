package udumeoli.TripPhoto.user.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.TripPhoto.common.entity.AuditMetadata

@Table("social_account")
data class SocialAccount(
    @Id
    val id: Long? = null,
    @Column("service_user_id")
    val serviceUserId: Long,
    val provider: String,
    @Column("provider_user_id")
    val providerUserId: String,
    @Column("provider_email")
    val providerEmail: String? = null,
    @Embedded.Nullable(prefix = "")
    val auditMetadata: AuditMetadata? = null,
)
