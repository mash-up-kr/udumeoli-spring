package udumeoli.tripphoto.kpi.event

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import udumeoli.tripphoto.kpi.entity.KpiEventRecord
import udumeoli.tripphoto.kpi.repository.KpiEventRepository

@Component
class KpiEventListener(
    private val kpiEventRepository: KpiEventRepository,
) {
    // 본 트랜잭션이 롤백되면 이벤트도 함께 사라져야 하므로 AFTER_COMMIT에서만 기록한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onKpiEvent(event: KpiEventOccurred) {
        kpiEventRepository.save(
            KpiEventRecord(
                eventType = event.type.name,
                serviceUserId = event.serviceUserId,
                metadata = event.metadata,
            ),
        )
    }
}
