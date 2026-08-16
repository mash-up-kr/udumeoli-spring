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

    companion object {
        // 서버가 정의한 프리셋 아바타 코드 범위. image 테이블에서 영구 예약된 id(1~4)와 대응한다.
        private val PRESET_PROFILE_IMAGE_CODES = 1L..4L

        fun isPresetProfileImage(profileImage: Long): Boolean = profileImage in PRESET_PROFILE_IMAGE_CODES
    }
}
