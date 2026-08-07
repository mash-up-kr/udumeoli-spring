package udumeoli.tripphoto.trip.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.tripphoto.common.entity.AuditMetadata

@Table("trip_record")
data class TripRecord(
    @Id
    val id: Long? = null,
    @Column("trip_id")
    val tripId: Long,
    @Column("service_user_id")
    val serviceUserId: Long,
    @Column("comment_text")
    val comment: String? = null,
    @Embedded.Empty(prefix = "")
    val auditMetadata: AuditMetadata = AuditMetadata(),
)
