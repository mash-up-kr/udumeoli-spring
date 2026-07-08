package udumeoli.tripphoto.party.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.party.entity.PartyMember

interface PartyMemberRepository : ListCrudRepository<PartyMember, Long> {
    fun findAllByPartyId(partyId: Long): List<PartyMember>

    fun findAllByServiceUserId(serviceUserId: Long): List<PartyMember>

    fun existsByPartyIdAndServiceUserId(
        partyId: Long,
        serviceUserId: Long,
    ): Boolean
}
