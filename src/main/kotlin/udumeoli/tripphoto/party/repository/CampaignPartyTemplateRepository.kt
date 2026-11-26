package udumeoli.tripphoto.party.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.party.entity.CampaignPartyTemplate

interface CampaignPartyTemplateRepository : ListCrudRepository<CampaignPartyTemplate, Long> {
    fun existsByPartyId(partyId: Long): Boolean
}
