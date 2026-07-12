package udumeoli.tripphoto.region.entity

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.tripphoto.common.entity.AuditMetadata

/**
 * 자연키(행정구역 코드) 엔티티.
 * id가 항상 채워져 있어 기본 isNew 판정(id == null)으로는 신규 저장이 UPDATE로 나가기 때문에
 * Persistable을 구현한다. 신규 행은 반드시 Region.of(...)로 생성해서 save() 해야 INSERT 된다.
 */
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
        /** 신규 지역 생성 팩토리 — save() 시 INSERT가 보장된다 */
        fun of(
            regionCode: String,
            regionName: String,
        ): Region = Region(regionCode, regionName).apply { new = true }
    }
}
