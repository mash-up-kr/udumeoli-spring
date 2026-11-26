package udumeoli.tripphoto.party.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("campaign_party_instance")
data class CampaignPartyInstance(
    @Id val id: Long? = null,
    val templatePartyId: Long,
    val fanUserId: Long,
    val partyId: Long,
)
