package udumeoli.tripphoto.party.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.tripphoto.common.entity.AuditMetadata

@Table("party")
data class Party(
    @Id
    val id: Long? = null,
    @Column("party_name")
    val partyName: String,
    @Column("invite_code")
    val inviteCode: String,
    @Column("owner_id")
    val ownerId: Long,
    @Embedded.Empty(prefix = "")
    val auditMetadata: AuditMetadata = AuditMetadata(),
) {
    fun isOwner(userId: Long): Boolean = ownerId == userId

    fun canLeave(userId: Long): Boolean = !isOwner(userId)

    fun canKick(targetUserId: Long): Boolean = !isOwner(targetUserId)
}
