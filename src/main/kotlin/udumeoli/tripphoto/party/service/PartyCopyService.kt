@file:Suppress("LongParameterList")

package udumeoli.tripphoto.party.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.common.i18n.MessageKeys
import udumeoli.tripphoto.party.dto.PartyPayload
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.event.PartyCopied
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripImage
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripImageRepository
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.service.UserService

/** Creates a separate party from any invite without changing the source party. */
@Service
class PartyCopyService(
    private val partyRepository: PartyRepository,
    private val partyMemberRepository: PartyMemberRepository,
    private val partyQueryService: PartyQueryService,
    private val inviteCodeIssuer: InviteCodeIssuer,
    private val userService: UserService,
    private val tripRepository: TripRepository,
    private val tripRecordRepository: TripRecordRepository,
    private val tripImageRepository: TripImageRepository,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${CAMPAIGN_COPY_SOURCE_PARTY_IDS:}") copySourcePartyIds: String = "",
) {
    private val copySourcePartyIds =
        copySourcePartyIds
            .split(',')
            .filter { it.isNotBlank() }
            .map { it.trim().toLong() }
            .toSet()

    fun isCampaignSource(partyId: Long): Boolean = partyId in copySourcePartyIds

    @Transactional
    fun copyParty(
        copiedByUserId: Long,
        inviteCode: String,
    ): PartyPayload {
        val user = userService.getCurrentUser(copiedByUserId)
        val code = InviteCode.parse(inviteCode)
        val source =
            partyRepository.findByInviteCode(code.value)
                ?: throw GraphQlDomainException(GraphQlErrorCode.INVALID_INVITE_CODE, MessageKeys.Invite.NOT_FOUND)
        val party = copy(source, copiedByUserId, user.nickname)
        return partyQueryService.toPayload(party)
    }

    private fun copy(
        source: Party,
        copiedByUserId: Long,
        copiedByNickname: String,
    ): Party {
        val sourcePartyId = requireNotNull(source.id)
        val sourceMemberIds = partyQueryService.memberUserIdsInJoinOrder(sourcePartyId)

        val party =
            try {
                partyRepository.save(
                    Party(
                        partyName = source.partyName,
                        inviteCode = inviteCodeIssuer.issue().value,
                        ownerId = source.ownerId,
                    ),
                )
            } catch (_: DuplicateKeyException) {
                throw GraphQlDomainException(GraphQlErrorCode.INVITE_CODE_CONFLICT, MessageKeys.Invite.CONFLICT)
            }
        val partyId = requireNotNull(party.id)
        sourceMemberIds.forEach { memberId ->
            partyMemberRepository.save(PartyMember(partyId = partyId, serviceUserId = memberId))
        }
        copyTrips(sourcePartyId, partyId)
        if (copiedByUserId !in sourceMemberIds) {
            partyMemberRepository.save(PartyMember(partyId = partyId, serviceUserId = copiedByUserId))
        }
        eventPublisher.publishEvent(
            PartyCopied(
                sourcePartyId = sourcePartyId,
                copiedPartyId = partyId,
                partyName = party.partyName,
                copiedByUserId = copiedByUserId,
                copiedByNickname = copiedByNickname,
            ),
        )
        return party
    }

    private fun copyTrips(
        sourcePartyId: Long,
        partyId: Long,
    ) {
        tripRepository.findAllByPartyId(sourcePartyId).forEach { sourceTrip ->
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
}
