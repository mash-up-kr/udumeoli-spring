package udumeoli.tripphoto.trip.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.ContextValue
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.common.graphql.requireCurrentUserId
import udumeoli.tripphoto.config.CurrentUserGraphQlInterceptor
import udumeoli.tripphoto.trip.dto.CreateTripInput
import udumeoli.tripphoto.trip.dto.TravelStatsPayload
import udumeoli.tripphoto.trip.dto.TripPayload
import udumeoli.tripphoto.trip.dto.UpdateTripInput
import udumeoli.tripphoto.trip.service.TripCommandService
import udumeoli.tripphoto.trip.service.TripQueryService

@Controller
class TripGraphQlController(
    private val tripQueryService: TripQueryService,
    private val tripCommandService: TripCommandService,
) {
    @QueryMapping
    fun trips(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument partyId: Long,
    ): List<TripPayload> = tripQueryService.trips(requireCurrentUserId(currentUserId), partyId)

    @QueryMapping
    fun tripsByRegion(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument partyId: Long,
        @Argument regionCode: String,
    ): List<TripPayload> = tripQueryService.tripsByRegion(requireCurrentUserId(currentUserId), partyId, regionCode)

    @QueryMapping
    fun trip(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument tripId: Long,
    ): TripPayload = tripQueryService.trip(requireCurrentUserId(currentUserId), tripId)

    @QueryMapping
    fun travelStats(
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
    fun updateTrip(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument input: UpdateTripInput,
    ): TripPayload = tripCommandService.updateTrip(requireCurrentUserId(currentUserId), input)

    @MutationMapping
    fun deleteTrip(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument tripId: Long,
    ): Long = tripCommandService.deleteTrip(requireCurrentUserId(currentUserId), tripId)
}
