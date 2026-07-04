package udumeoli.TripPhoto.trip.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.TripPhoto.trip.entity.Trip

interface TripRepository : ListCrudRepository<Trip, Long> {
    fun findAllByPartyId(partyId: Long): List<Trip>
    fun findAllByRegionCode(regionCode: String): List<Trip>
    fun findAllByCreatedBy(createdBy: Long): List<Trip>
}
