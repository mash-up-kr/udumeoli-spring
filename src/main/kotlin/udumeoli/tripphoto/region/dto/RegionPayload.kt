package udumeoli.tripphoto.region.dto

import udumeoli.tripphoto.region.entity.Region

data class RegionPayload(
    val code: String,
    val name: String,
)

fun Region.toPayload(): RegionPayload =
    RegionPayload(
        code = regionCode,
        name = regionName,
    )
