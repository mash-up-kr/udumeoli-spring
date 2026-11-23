package udumeoli.tripphoto.kpi.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.kpi.entity.KpiEventRecord
import java.time.LocalDateTime

interface KpiEventRepository : ListCrudRepository<KpiEventRecord, Long> {
    fun countByEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
        eventType: String,
        start: LocalDateTime,
        end: LocalDateTime,
    ): Long
}
