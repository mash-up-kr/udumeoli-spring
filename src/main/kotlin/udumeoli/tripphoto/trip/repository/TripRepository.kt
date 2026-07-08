package udumeoli.tripphoto.trip.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.trip.entity.Trip

interface TripRepository : ListCrudRepository<Trip, Long> {
    fun findAllByPartyId(partyId: Long): List<Trip>
    fun findAllByRegionCode(regionCode: String): List<Trip>
    fun findAllByCreatedBy(createdBy: Long): List<Trip>
}
