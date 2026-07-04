package udumeoli.TripPhoto.user.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.TripPhoto.user.entity.ServiceUser

interface ServiceUserRepository : ListCrudRepository<ServiceUser, Long> {
    fun findByNickname(nickname: String): ServiceUser?
}
