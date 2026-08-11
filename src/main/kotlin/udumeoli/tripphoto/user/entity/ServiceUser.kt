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
    @Column("profile_image")
    val profileImage: Long,
    @Embedded.Empty(prefix = "")
    val auditMetadata: AuditMetadata = AuditMetadata(),
) {
    fun updateProfile(
        nickname: String,
        profileImage: Long?,
    ): ServiceUser =
        copy(
            nickname = nickname,
            profileImage = profileImage ?: this.profileImage,
        )
}
