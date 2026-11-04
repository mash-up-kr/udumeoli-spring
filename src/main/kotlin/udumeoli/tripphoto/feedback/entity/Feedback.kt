package udumeoli.tripphoto.feedback.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/** 마이페이지 "서비스 피드백 보내기"로 받은 별점 + 의견. 한 번 쌓이면 고치지 않는다. */
@Table("feedback")
data class Feedback(
    @Id
    val id: Long? = null,
    @Column("service_user_id")
    val serviceUserId: Long,
    val rating: Int,
    val content: String,
    @Column("app_version")
    val appVersion: String,
    @Column("device_info")
    val deviceInfo: String,
    @CreatedDate
    @Column("created_at")
    val createdAt: LocalDateTime? = null,
) {
    companion object {
        val RATING_RANGE = 1..5
        const val CONTENT_MAX_LENGTH = 5_000

        // 컬럼 길이(V8)와 맞춘다.
        const val APP_VERSION_MAX_LENGTH = 50
        const val DEVICE_INFO_MAX_LENGTH = 1_000
    }
}
