package udumeoli.tripphoto.kpi.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/** [KpiEventOccurred]가 커밋된 뒤 한 줄씩 쌓이는 집계용 로그. 수정되지 않는다. */
@Table("kpi_event")
data class KpiEventRecord(
    @Id
    val id: Long? = null,
    @Column("event_type")
    val eventType: String,
    @Column("service_user_id")
    val serviceUserId: Long,
    @Column("occurred_at")
    val occurredAt: LocalDateTime = LocalDateTime.now(),
    val metadata: String? = null,
)
