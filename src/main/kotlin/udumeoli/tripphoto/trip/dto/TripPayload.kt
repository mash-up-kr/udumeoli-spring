package udumeoli.tripphoto.trip.dto

import udumeoli.tripphoto.trip.entity.TripKeyword
import java.time.LocalDate
import java.time.LocalDateTime

data class TripPayload(
    val id: Long,
    val regionCode: Int,
    val keyword: TripKeyword,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** 이 지역의 몇 번째 방문인지 (1부터). "N번째 방문" 라벨. */
    val visitSequence: Int,
    /** 팟 멤버 전원 — 미기록 멤버 포함. 나 최상단, 이후 가입 순서. */
    val records: List<TripRecordPayload>,
    val createdAt: LocalDateTime,
)
