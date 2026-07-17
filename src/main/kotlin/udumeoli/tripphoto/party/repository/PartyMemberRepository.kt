package udumeoli.tripphoto.party.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.party.entity.PartyMember

interface PartyMemberRepository : ListCrudRepository<PartyMember, Long> {
    fun findAllByPartyId(partyId: Long): List<PartyMember>

    fun findAllByServiceUserId(serviceUserId: Long): List<PartyMember>

    fun findByPartyIdAndServiceUserId(
        partyId: Long,
        serviceUserId: Long,
    ): PartyMember?

    fun countByPartyId(partyId: Long): Long

    fun existsByPartyIdAndServiceUserId(
        partyId: Long,
        serviceUserId: Long,
    ): Boolean
}
