package udumeoli.tripphoto.party.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.party.entity.CampaignPlace

interface CampaignPlaceRepository : ListCrudRepository<CampaignPlace, Long> {
    fun findAllByTemplatePartyIdOrderByPlaceOrder(templatePartyId: Long): List<CampaignPlace>
}
