@file:Suppress("LongParameterList")

package udumeoli.tripphoto.party.service

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import udumeoli.tripphoto.party.entity.CampaignPartyInstance
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.event.CampaignFanJoined
import udumeoli.tripphoto.party.repository.CampaignPartyInstanceRepository
import udumeoli.tripphoto.party.repository.CampaignPartyTemplateRepository
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripImage
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripImageRepository
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository

/** A campaign invite always creates one private, six-member party for the joining fan. */
@Service
class CampaignPartyService(
    private val partyRepository: PartyRepository,
    private val partyMemberRepository: PartyMemberRepository,
    private val campaignPartyInstanceRepository: CampaignPartyInstanceRepository,
    private val campaignPartyTemplateRepository: CampaignPartyTemplateRepository,
    private val partyQueryService: PartyQueryService,
    private val inviteCodeIssuer: InviteCodeIssuer,
    private val tripRepository: TripRepository,
    private val tripRecordRepository: TripRecordRepository,
    private val tripImageRepository: TripImageRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun isTemplate(partyId: Long): Boolean = campaignPartyTemplateRepository.existsByPartyId(partyId)

    fun alreadyJoined(
        templateId: Long,
        fanUserId: Long,
    ): Boolean = campaignPartyInstanceRepository.existsByTemplatePartyIdAndFanUserId(templateId, fanUserId)

    /** Called inside PartyJoinService's transaction, while the template row is locked. */
    fun createForFan(
        template: Party,
        fanUserId: Long,
        fanNickname: String,
    ): Party {
        val templateId = requireNotNull(template.id)
        check(!campaignPartyInstanceRepository.existsByTemplatePartyIdAndFanUserId(templateId, fanUserId))
        val personaIds = partyQueryService.memberUserIdsInJoinOrder(templateId)
        check(personaIds.size == PERSONA_COUNT) { "Campaign template must contain five personas" }

        val party =
            partyRepository.save(
                Party(
                    partyName = template.partyName,
                    inviteCode = inviteCodeIssuer.issue(),
                    ownerId = template.ownerId,
                ),
            )
        val partyId = requireNotNull(party.id)
        personaIds.forEach { personaId ->
            partyMemberRepository.save(PartyMember(partyId = partyId, serviceUserId = personaId))
        }
        copyTrips(templateId, partyId)
        partyMemberRepository.save(PartyMember(partyId = partyId, serviceUserId = fanUserId))
        campaignPartyInstanceRepository.save(
            CampaignPartyInstance(
                templatePartyId = templateId,
                fanUserId = fanUserId,
                partyId = partyId,
            ),
        )
        eventPublisher.publishEvent(
            CampaignFanJoined(
                templatePartyId = templateId,
                partyId = partyId,
                partyName = party.partyName,
                fanUserId = fanUserId,
                fanNickname = fanNickname,
            ),
        )
        return party
    }

    private fun copyTrips(
        templateId: Long,
        partyId: Long,
    ) {
        tripRepository.findAllByPartyId(templateId).forEach { sourceTrip ->
            val trip =
                tripRepository.save(
                    Trip(
                        partyId = partyId,
                        regionCode = sourceTrip.regionCode,
                        createdBy = sourceTrip.createdBy,
                    ),
                )
            copyRecords(requireNotNull(sourceTrip.id), requireNotNull(trip.id))
        }
    }

    private fun copyRecords(
        sourceTripId: Long,
        tripId: Long,
    ) {
        tripRecordRepository.findAllByTripId(sourceTripId).forEach { sourceRecord ->
            val record =
                tripRecordRepository.save(
                    TripRecord(
                        tripId = tripId,
                        serviceUserId = sourceRecord.serviceUserId,
                        keyword = sourceRecord.keyword,
                        comment = sourceRecord.comment,
                    ),
                )
            copyImages(requireNotNull(sourceRecord.id), requireNotNull(record.id))
        }
    }

    private fun copyImages(
        sourceRecordId: Long,
        recordId: Long,
    ) {
        tripImageRepository.findAllByTripRecordId(sourceRecordId).forEach { sourceLink ->
            // Media is shared by image ID; ImageService removes it only after the last link is gone.
            tripImageRepository.save(
                TripImage(
                    tripRecordId = recordId,
                    imageId = sourceLink.imageId,
                    imageDate = sourceLink.imageDate,
                ),
            )
        }
    }

    private companion object {
        const val PERSONA_COUNT = 5
    }
}
