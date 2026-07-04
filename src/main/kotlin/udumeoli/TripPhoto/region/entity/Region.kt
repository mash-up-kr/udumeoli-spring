package udumeoli.TripPhoto.region.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.TripPhoto.common.entity.AuditMetadata

@Table("regions")
data class Region(
    @Id
    val regionCode: String,
    val regionName: String,
    @Embedded.Nullable(prefix = "")
    val auditMetadata: AuditMetadata? = null,
)
