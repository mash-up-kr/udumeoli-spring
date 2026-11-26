package udumeoli.tripphoto.region.graphql

import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.auth.annotation.LoginUser
import udumeoli.tripphoto.region.service.RegionNamePayload
import udumeoli.tripphoto.region.service.RegionNameService
import udumeoli.tripphoto.trip.dto.MapCellPayload
import udumeoli.tripphoto.trip.dto.TripPayload
import udumeoli.tripphoto.trip.dto.VisitedRegionPayload

@Controller
class RegionNameGraphQlController(
    private val regionNameService: RegionNameService,
) {
    @QueryMapping
    @Suppress("UnusedParameter")
    fun regions(
        @LoginUser currentUserId: Long,
        environment: DataFetchingEnvironment,
    ): List<RegionNamePayload> = regionNameService.all(environment.locale)

    @SchemaMapping(typeName = "Trip", field = "regionName")
    fun tripRegionName(
        trip: TripPayload,
        environment: DataFetchingEnvironment,
    ): String = regionNameService.name(trip.regionCode, environment.locale)

    @SchemaMapping(typeName = "VisitedRegion", field = "regionName")
    fun visitedRegionName(
        region: VisitedRegionPayload,
        environment: DataFetchingEnvironment,
    ): String = regionNameService.name(region.regionCode, environment.locale)

    @SchemaMapping(typeName = "MapCell", field = "regionName")
    fun mapCellRegionName(
        cell: MapCellPayload,
        environment: DataFetchingEnvironment,
    ): String = regionNameService.name(cell.regionCode, environment.locale)
}
