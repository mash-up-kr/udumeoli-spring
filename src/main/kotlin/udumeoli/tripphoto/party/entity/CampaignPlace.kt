package udumeoli.tripphoto.party.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("campaign_place")
data class CampaignPlace(
    @Id val id: Long? = null,
    val templatePartyId: Long,
    val placeOrder: Int,
    val placeName: String,
    val personaUserId: Long,
    val visitPolicy: String = "PUBLIC",
)
