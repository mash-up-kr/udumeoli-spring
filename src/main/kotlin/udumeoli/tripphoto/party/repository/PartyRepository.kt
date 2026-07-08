package udumeoli.tripphoto.party.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.party.entity.Party

interface PartyRepository : ListCrudRepository<Party, Long> {
    fun findByInviteCode(inviteCode: String): Party?
}
