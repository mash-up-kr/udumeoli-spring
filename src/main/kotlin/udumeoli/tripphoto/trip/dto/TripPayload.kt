package udumeoli.tripphoto.trip.dto

import java.time.LocalDateTime

/** 팟이 한 지역에 찍은 핀 (GraphQL `Trip`). */
data class TripPayload(
    val id: Long,
    val regionCode: String,
    /** 팟 멤버 전원 — 미기록 멤버 포함. 나 최상단, 이후 가입 순서. */
    val records: List<TripRecordPayload>,
    val createdAt: LocalDateTime,
)
