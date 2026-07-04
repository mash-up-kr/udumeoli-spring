package udumeoli.TripPhoto.trip.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.TripPhoto.trip.entity.TripImage

interface TripImageRepository : ListCrudRepository<TripImage, Long> {
    fun findAllByTripId(tripId: Long): List<TripImage>
    fun findAllByImageId(imageId: Long): List<TripImage>
    fun existsByTripIdAndImageId(tripId: Long, imageId: Long): Boolean
}
