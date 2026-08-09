package udumeoli.tripphoto.region.repository

import org.springframework.data.repository.Repository
import udumeoli.tripphoto.region.entity.Region

/**
 * region은 마이그레이션이 시딩하는 정적 데이터다.
 * save/delete가 새어 나가지 않도록 CrudRepository를 상속하지 않고 필요한 조회만 열어둔다.
 */
interface RegionRepository : Repository<Region, String> {
    fun existsByRegionCode(regionCode: String): Boolean
}
