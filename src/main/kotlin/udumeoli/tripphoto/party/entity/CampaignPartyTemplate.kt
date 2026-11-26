package udumeoli.tripphoto.party.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("campaign_party_template")
data class CampaignPartyTemplate(
    @Id val id: Long? = null,
    val partyId: Long,
    val campaignKey: String,
    val artistName: String,
    val regionCode: String,
)
