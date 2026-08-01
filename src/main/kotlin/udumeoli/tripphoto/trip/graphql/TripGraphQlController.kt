package udumeoli.tripphoto.trip.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.ContextValue
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.common.graphql.requireCurrentUserId
import udumeoli.tripphoto.config.CurrentUserGraphQlInterceptor
import udumeoli.tripphoto.trip.dto.CreateTripInput
import udumeoli.tripphoto.trip.dto.RecordTripInput
import udumeoli.tripphoto.trip.dto.RegionCardPayload
import udumeoli.tripphoto.trip.dto.TravelStatsPayload
import udumeoli.tripphoto.trip.dto.TripPayload
import udumeoli.tripphoto.trip.service.TripCommandService
import udumeoli.tripphoto.trip.service.TripQueryService
import udumeoli.tripphoto.trip.service.TripRegionQueryService

@Controller
class TripGraphQlController(
    private val tripQueryService: TripQueryService,
    private val tripCommandService: TripCommandService,
    private val tripRegionQueryService: TripRegionQueryService,
) {
    @QueryMapping
    fun partyTripsAll(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument partyId: Long,
    ): List<TripPayload> = tripQueryService.trips(requireCurrentUserId(currentUserId), partyId)

    @QueryMapping
    fun partyTripsByRegion(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument partyId: Long,
        @Argument regionCode: Int,
    ): List<TripPayload> =
        tripQueryService.tripsByRegion(requireCurrentUserId(currentUserId), partyId, regionCode.toString())

    @QueryMapping
    fun partyTripsByRegionsStats(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument partyId: Long,
    ): List<RegionCardPayload> = tripRegionQueryService.regionCards(requireCurrentUserId(currentUserId), partyId)

    @QueryMapping
    fun partyTravelStats(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument partyId: Long,
    ): TravelStatsPayload = tripQueryService.travelStats(requireCurrentUserId(currentUserId), partyId)

    @MutationMapping
    fun createTrip(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument input: CreateTripInput,
    ): TripPayload = tripCommandService.createTrip(requireCurrentUserId(currentUserId), input)

    @MutationMapping
    fun recordTrip(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument input: RecordTripInput,
    ): TripPayload = tripCommandService.recordTrip(requireCurrentUserId(currentUserId), input)

    @MutationMapping
    fun deleteTripRecord(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument tripId: Long,
    ): TripPayload? = tripCommandService.deleteTripRecord(requireCurrentUserId(currentUserId), tripId)
}
