package udumeoli.tripphoto.trip.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.trip.entity.Trip

interface TripRepository : ListCrudRepository<Trip, Long> {
    fun findAllByPartyId(partyId: Long): List<Trip>

    /** 지역당 핀은 하나뿐이다(uq_trip_party_region). */
    fun findByPartyIdAndRegionCode(
        partyId: Long,
        regionCode: String,
    ): Trip?
}
