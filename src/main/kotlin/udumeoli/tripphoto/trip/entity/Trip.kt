package udumeoli.tripphoto.trip.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.tripphoto.common.entity.AuditMetadata
import java.time.LocalDate

@Table("trip")
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
    val createdBy: Long? = null,
    @Embedded.Empty(prefix = "")
    val auditMetadata: AuditMetadata = AuditMetadata(),
)
