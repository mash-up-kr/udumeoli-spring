package udumeoli.tripphoto.party.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.party.entity.CampaignPartyInstance

interface CampaignPartyInstanceRepository : ListCrudRepository<CampaignPartyInstance, Long> {
    fun findByPartyId(partyId: Long): CampaignPartyInstance?

    fun existsByTemplatePartyIdAndFanUserId(
        templatePartyId: Long,
        fanUserId: Long,
    ): Boolean

    fun findByPartyIdAndFanUserId(
        partyId: Long,
        fanUserId: Long,
    ): CampaignPartyInstance?
}
