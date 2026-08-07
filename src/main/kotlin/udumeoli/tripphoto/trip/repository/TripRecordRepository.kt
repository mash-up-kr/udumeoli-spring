package udumeoli.tripphoto.trip.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.trip.entity.TripRecord

interface TripRecordRepository : ListCrudRepository<TripRecord, Long> {
    fun findAllByTripId(tripId: Long): List<TripRecord>

    fun findAllByTripIdIn(tripIds: Collection<Long>): List<TripRecord>

    fun findByTripIdAndServiceUserId(
        tripId: Long,
        serviceUserId: Long,
    ): TripRecord?
}
