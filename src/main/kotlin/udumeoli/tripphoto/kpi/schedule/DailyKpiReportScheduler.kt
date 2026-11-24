package udumeoli.tripphoto.kpi.schedule

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import udumeoli.tripphoto.kpi.entity.KpiEventType
import udumeoli.tripphoto.kpi.notification.DiscordNotifier
import udumeoli.tripphoto.kpi.repository.KpiEventRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * 전날(KST 00:00~24:00) 가입/탈퇴/여행 기록 완료 건수를 집계해 디스코드로 보낸다.
 * 컨테이너 TZ가 Asia/Seoul로 고정돼 있어(docker-compose.yml) kpi_event.occurred_at도
 * 이미 KST 벽시계 값이므로, 여기서 쓰는 LocalDateTime 경계도 그대로 KST로 다루면 된다.
 */
@Component
class DailyKpiReportScheduler(
    private val kpiEventRepository: KpiEventRepository,
    private val discordNotifier: DiscordNotifier,
) {
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
    fun reportYesterday() {
        val yesterday = LocalDate.now(SEOUL).minusDays(1)
        val start = yesterday.atStartOfDay()
        val end = yesterday.plusDays(1).atStartOfDay()

        val counts =
            KpiEventType.entries.associateWith { type ->
                kpiEventRepository.countByEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                    type.name,
                    start,
                    end,
                )
            }

        discordNotifier.send(formatMessage(yesterday, counts))
    }

    private fun formatMessage(
        date: LocalDate,
        counts: Map<KpiEventType, Long>,
    ): String =
        """
        **$date KPI 리포트**
        가입: ${counts[KpiEventType.SIGNUP] ?: 0}명
        탈퇴: ${counts[KpiEventType.WITHDRAWAL] ?: 0}명
        여행 기록 완료: ${counts[KpiEventType.TRIP_RECORDED] ?: 0}건
        """.trimIndent()

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
