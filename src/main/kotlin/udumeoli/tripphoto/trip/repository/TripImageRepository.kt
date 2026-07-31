package udumeoli.tripphoto.trip.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.trip.entity.TripImage

interface TripImageRepository : ListCrudRepository<TripImage, Long> {
    fun findAllByTripId(tripId: Long): List<TripImage>

    fun findAllByTripIdIn(tripIds: Collection<Long>): List<TripImage>
}
