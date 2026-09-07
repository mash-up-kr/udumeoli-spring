package udumeoli.tripphoto.trip.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.tripphoto.common.entity.AuditMetadata

/**
 * 팟이 한 지역에 찍은 핀. 팟·지역 조합마다 하나뿐이다(uq_trip_party_region).
 *
 * 기간도 키워드도 여기 없다 — 기획에서 여행 기간 입력이 사라지고 키워드는 올리는 사람이 각자 고르게 되면서,
 * 핀 자체는 "이 팟이 이 지역에 다녀왔다"는 사실만 남기고 나머지는 [TripRecord]로 내려갔다.
 */
@Table("trip")
data class Trip(
    @Id
    val id: Long? = null,
    @Column("party_id")
    val partyId: Long,
    @Column("region_code")
    val regionCode: String,
    @Column("created_by")
    val createdBy: Long? = null,
    @Embedded.Empty(prefix = "")
    val auditMetadata: AuditMetadata = AuditMetadata(),
)
