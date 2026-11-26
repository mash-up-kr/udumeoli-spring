package udumeoli.tripphoto.party.repository

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.party.entity.Party

interface PartyRepository : ListCrudRepository<Party, Long> {
    fun findByInviteCode(inviteCode: String): Party?

    fun existsBySourcePartyIdAndInvitedUserId(
        sourcePartyId: Long,
        invitedUserId: Long,
    ): Boolean

    fun findBySourcePartyIdAndInvitedUserId(
        sourcePartyId: Long,
        invitedUserId: Long,
    ): Party?

    fun existsByInviteCode(inviteCode: String): Boolean

    @Query("SELECT * FROM party WHERE invite_code = :inviteCode FOR UPDATE")
    fun findByInviteCodeForUpdate(inviteCode: String): Party?
}
