package udumeoli.tripphoto.trip.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.trip.entity.Trip

interface TripRepository : ListCrudRepository<Trip, Long> {
    fun findAllByPartyId(partyId: Long): List<Trip>

    fun findAllByPartyIdAndRegionCode(
        partyId: Long,
        regionCode: String,
    ): List<Trip>
}
