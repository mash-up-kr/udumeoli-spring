package udumeoli.tripphoto.trip.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.trip.entity.TripImage

interface TripImageRepository : ListCrudRepository<TripImage, Long> {
    fun findAllByTripRecordId(tripRecordId: Long): List<TripImage>

    fun findAllByTripRecordIdIn(tripRecordIds: Collection<Long>): List<TripImage>
}
