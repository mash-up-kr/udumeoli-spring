package udumeoli.TripPhoto.party.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Embedded
import org.springframework.data.relational.core.mapping.Table
import udumeoli.TripPhoto.common.entity.AuditMetadata

@Table("party")
data class Party(
    @Id
    val id: Long? = null,
    @Column("party_name")
    val partyName: String,
    @Column("invite_code")
    val inviteCode: String,
    @Embedded.Nullable(prefix = "")
    val auditMetadata: AuditMetadata? = null,
)
