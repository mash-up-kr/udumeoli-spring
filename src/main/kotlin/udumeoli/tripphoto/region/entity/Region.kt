package udumeoli.tripphoto.region.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.tripphoto.common.entity.AuditMetadata

/**
 * 행정구역. 마이그레이션이 시딩하는 정적 데이터라 애플리케이션에서는 **읽기만** 한다.
 */
@Table("region")
data class Region(
    @Id
    val regionCode: String,
    val regionName: String,
    @Embedded.Empty(prefix = "")
    val auditMetadata: AuditMetadata = AuditMetadata(),
)
