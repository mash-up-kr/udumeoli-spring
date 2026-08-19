package udumeoli.tripphoto.trip.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.auth.annotation.LoginUser
import udumeoli.tripphoto.trip.dto.CreateTripInput
import udumeoli.tripphoto.trip.dto.PartyMapOverviewPayload
import udumeoli.tripphoto.trip.dto.RecordTripInput
import udumeoli.tripphoto.trip.dto.TripPayload
import udumeoli.tripphoto.trip.dto.TripStatsPayload
import udumeoli.tripphoto.trip.dto.VisitedRegionPayload
import udumeoli.tripphoto.trip.service.PartyMapQueryService
import udumeoli.tripphoto.trip.service.TripCommandService
import udumeoli.tripphoto.trip.service.TripQueryService

@Controller
class TripGraphQlController(
    private val tripQueryService: TripQueryService,
    private val tripCommandService: TripCommandService,
    private val partyMapQueryService: PartyMapQueryService,
) {
    @QueryMapping
    fun partyTrips(
        @LoginUser currentUserId: Long,
        @Argument partyId: Long,
    ): List<TripPayload> = tripQueryService.trips(currentUserId, partyId)

    @QueryMapping
    fun partyTripsInRegion(
        @LoginUser currentUserId: Long,
        @Argument partyId: Long,
        @Argument regionCode: String,
    ): List<TripPayload> = tripQueryService.tripsByRegion(currentUserId, partyId, regionCode)

    @QueryMapping
    fun partyVisitedRegions(
        @LoginUser currentUserId: Long,
        @Argument partyId: Long,
    ): List<VisitedRegionPayload> = tripQueryService.visitedRegions(currentUserId, partyId)

    @QueryMapping
    fun partyTripStats(
        @LoginUser currentUserId: Long,
        @Argument partyId: Long,
    ): TripStatsPayload = tripQueryService.tripStats(currentUserId, partyId)

    @QueryMapping
    fun partyMapOverview(
        @LoginUser currentUserId: Long,
        @Argument partyId: Long,
    ): PartyMapOverviewPayload = partyMapQueryService.mapOverview(currentUserId, partyId)

    @MutationMapping
    fun createTrip(
        @LoginUser currentUserId: Long,
        @Argument input: CreateTripInput,
    ): TripPayload = tripCommandService.createTrip(currentUserId, input)

    @MutationMapping
    fun recordTrip(
        @LoginUser currentUserId: Long,
        @Argument input: RecordTripInput,
    ): TripPayload = tripCommandService.recordTrip(currentUserId, input)

    @MutationMapping
    fun deleteTripRecord(
        @LoginUser currentUserId: Long,
        @Argument tripId: Long,
    ): TripPayload? = tripCommandService.deleteTripRecord(currentUserId, tripId)
}
