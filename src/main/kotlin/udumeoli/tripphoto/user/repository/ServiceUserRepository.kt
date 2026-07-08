package udumeoli.tripphoto.user.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.user.entity.ServiceUser

interface ServiceUserRepository : ListCrudRepository<ServiceUser, Long> {
    fun findByNickname(nickname: String): ServiceUser?
}
