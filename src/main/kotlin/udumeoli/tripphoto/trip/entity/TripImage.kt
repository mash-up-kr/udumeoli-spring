package udumeoli.tripphoto.trip.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDate
import java.time.LocalDateTime

@Table("trip_image")
data class TripImage(
    @Id
    val id: Long? = null,
    @Column("trip_record_id")
    val tripRecordId: Long,
    @Column("image_id")
    val imageId: Long,
    // 사진 촬영일. 갤러리 최신순 정렬의 기준이며, 미전달 시 등록 순서로 밀린다.
    @Column("image_date")
    val imageDate: LocalDate? = null,
    @CreatedDate
    @Column("created_at")
    val createdAt: LocalDateTime? = null,
)
