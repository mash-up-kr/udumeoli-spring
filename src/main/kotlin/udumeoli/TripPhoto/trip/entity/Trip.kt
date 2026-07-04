package udumeoli.TripPhoto.trip.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.TripPhoto.common.entity.AuditMetadata
import java.time.LocalDate

@Table("trips")
data class Trip(
    @Id
    val id: Long? = null,
    @Column("party_id")
    val partyId: Long,
    @Column("region_code")
    val regionCode: String,
    val color: String? = null,
    @Column("start_date")
    val startDate: LocalDate,
    @Column("end_date")
    val endDate: LocalDate,
    @Column("created_by")
    val createdBy: Long,
    @Embedded.Nullable(prefix = "")
    val auditMetadata: AuditMetadata? = null,
)
