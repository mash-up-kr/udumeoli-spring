package udumeoli.tripphoto.user.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.tripphoto.common.entity.AuditMetadata

@Table("service_user")
data class ServiceUser(
    @Id
    val id: Long? = null,
    val nickname: String,
    @Column("profile_image_url")
    val profileImageUrl: String = "DEFAULT",
    @Embedded.Nullable(prefix = "")
    val auditMetadata: AuditMetadata? = null,
)
