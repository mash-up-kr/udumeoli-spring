package udumeoli.tripphoto.region.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.region.entity.Region

interface RegionRepository : ListCrudRepository<Region, String> {
    fun findByRegionName(regionName: String): Region?
}
