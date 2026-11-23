package udumeoli.tripphoto.kpi.event

import udumeoli.tripphoto.kpi.entity.KpiEventType

/**
 * 가입/탈퇴/여행 기록 완료 시점에 publish되는 애플리케이션 이벤트.
 * [KpiEventListener]가 트랜잭션 커밋 후 kpi_event에 한 줄로 기록한다.
 */
data class KpiEventOccurred(
    val type: KpiEventType,
    val serviceUserId: Long,
    val metadata: String? = null,
)
