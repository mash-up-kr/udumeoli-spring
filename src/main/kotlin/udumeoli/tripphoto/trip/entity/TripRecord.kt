package udumeoli.tripphoto.trip.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.tripphoto.common.entity.AuditMetadata

/**
 * 핀 하나에 멤버 1명이 남긴 기록 — 사진 1장 + 키워드 + 코멘트.
 * 키워드가 여기 있는 건 같은 지역이라도 올리는 사람마다 다른 키워드를 고르기 때문이다.
 */
@Table("trip_record")
data class TripRecord(
    @Id
    val id: Long? = null,
    @Column("trip_id")
    val tripId: Long,
    @Column("service_user_id")
    val serviceUserId: Long,
    val keyword: TripKeyword,
    @Column("comment_text")
    val comment: String? = null,
    @Embedded.Empty(prefix = "")
    val auditMetadata: AuditMetadata = AuditMetadata(),
)
