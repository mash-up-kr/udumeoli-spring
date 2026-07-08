package udumeoli.tripphoto.party.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("party_member")
data class PartyMember(
    @Id
    val id: Long? = null,
    @Column("party_id")
    val partyId: Long,
    @Column("service_user_id")
    val serviceUserId: Long,
)
