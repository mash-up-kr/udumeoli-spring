package udumeoli.TripPhoto.party.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.TripPhoto.party.entity.Party

interface PartyRepository : ListCrudRepository<Party, Long> {
    fun findByInviteCode(inviteCode: String): Party?
}
