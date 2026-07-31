package udumeoli.tripphoto.region.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.tripphoto.common.entity.AuditMetadata

@Table("region")
data class Region(
    @Id
    val regionCode: String,
    val regionName: String,
    @Embedded.Empty(prefix = "")
    val auditMetadata: AuditMetadata = AuditMetadata(),
) : Persistable<String> {
    @Transient
    private var new: Boolean = false

    override fun getId(): String = regionCode

    override fun isNew(): Boolean = new

    companion object {
        fun of(
            regionCode: String,
            regionName: String,
        ): Region = Region(regionCode, regionName).apply { new = true }
    }
}
