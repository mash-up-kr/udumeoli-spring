package udumeoli.TripPhoto.user.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.TripPhoto.user.entity.SocialAccount

interface SocialAccountRepository : ListCrudRepository<SocialAccount, Long> {
    fun findByProviderAndProviderUserId(provider: String, providerUserId: String): SocialAccount?
    fun findAllByServiceUserId(serviceUserId: Long): List<SocialAccount>
}
