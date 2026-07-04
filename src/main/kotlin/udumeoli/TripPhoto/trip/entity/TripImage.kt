package udumeoli.TripPhoto.trip.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDate
import java.time.LocalDateTime

@Table("trip_images")
data class TripImage(
    @Id
    val id: Long? = null,
    @Column("trip_id")
    val tripId: Long,
    @Column("image_id")
    val imageId: Long,
    @Column("image_date")
    val imageDate: LocalDate? = null,
    @Column("created_at")
    val createdAt: LocalDateTime? = null,
)
