package udumeoli.TripPhoto.region.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.TripPhoto.region.entity.Region

interface RegionRepository : ListCrudRepository<Region, String> {
    fun findByRegionName(regionName: String): Region?
}
