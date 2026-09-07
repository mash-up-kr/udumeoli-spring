package udumeoli.tripphoto.trip.service

import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.party.service.PartyQueryService
import udumeoli.tripphoto.region.repository.RegionRepository
import udumeoli.tripphoto.trip.dto.CreateTripInput
import udumeoli.tripphoto.trip.dto.RecordTripInput
import udumeoli.tripphoto.trip.dto.TripImageInput
import udumeoli.tripphoto.trip.dto.TripPayload
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripKeyword
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository

/**
 * 두 뮤테이션이 하는 일은 같다 — "이 지역에 내 기록을 남긴다". 핀을 어떻게 지목하느냐만 다르다.
 *
 * - createTrip: 지역 코드로 지목. 핀이 없으면 만든다. (지도에서 + 버튼을 눌러 들어온 경우)
 * - recordTrip: 이미 있는 핀을 id로 지목. (팟원이 먼저 찍어 둔 지역에 내 사진을 얹는 경우)
 * - deleteTripRecord: 내 기록 삭제, 마지막 기록이면 핀도 함께 정리
 */
@Service
class TripCommandService(
    private val tripRepository: TripRepository,
    private val tripRecordRepository: TripRecordRepository,
    private val regionRepository: RegionRepository,
    private val partyQueryService: PartyQueryService,
    private val tripQueryService: TripQueryService,
    private val tripImageWriter: TripImageWriter,
) {
    @Transactional
    fun createTrip(
        currentUserId: Long,
        input: CreateTripInput,
    ): TripPayload {
        partyQueryService.requireMember(input.partyId, currentUserId)
        if (!regionRepository.existsByRegionCode(input.regionCode)) {
            throw GraphQlDomainException(
                GraphQlErrorCode.REGION_NOT_FOUND,
                "존재하지 않는 지역입니다: ${input.regionCode}",
            )
        }

        val trip = pinOf(input.partyId, input.regionCode, currentUserId)
        writeRecord(requireNotNull(trip.id), currentUserId, input.keyword, input.image, input.comment)
        return tripQueryService.toPayload(currentUserId, trip)
    }

    @Transactional
    fun recordTrip(
        currentUserId: Long,
        input: RecordTripInput,
    ): TripPayload {
        val trip = tripQueryService.requireTrip(input.tripId)
        partyQueryService.requireMember(trip.partyId, currentUserId)

        writeRecord(input.tripId, currentUserId, input.keyword, input.image, input.comment)
        return tripQueryService.toPayload(currentUserId, trip)
    }

    @Transactional
    fun deleteTripRecord(
        currentUserId: Long,
        tripId: Long,
    ): TripPayload? {
        val trip = tripQueryService.requireTrip(tripId)
        partyQueryService.requireMember(trip.partyId, currentUserId)

        val record =
            tripRecordRepository.findByTripIdAndServiceUserId(tripId, currentUserId)
                ?: throw GraphQlDomainException(
                    GraphQlErrorCode.TRIP_RECORD_NOT_FOUND,
                    "삭제할 내 기록이 없습니다.",
                )
        tripImageWriter.setImages(requireNotNull(record.id), emptyList())
        tripRecordRepository.delete(record)

        if (tripRecordRepository.findAllByTripId(tripId).isEmpty()) {
            tripRepository.delete(trip)
            return null
        }
        return tripQueryService.toPayload(currentUserId, trip)
    }

    /**
     * 그 지역의 핀을 가져온다. 아직 아무도 안 찍었으면 새로 찍는다.
     *
     * 두 사람이 같은 지역을 동시에 찍으면 uq_trip_party_region이 한쪽을 막는데,
     * 그건 실패가 아니라 "먼저 찍은 핀이 이미 있다"는 뜻이라 그 핀을 다시 읽어 이어 쓴다.
     */
    private fun pinOf(
        partyId: Long,
        regionCode: String,
        currentUserId: Long,
    ): Trip =
        tripRepository.findByPartyIdAndRegionCode(partyId, regionCode)
            ?: try {
                tripRepository.save(Trip(partyId = partyId, regionCode = regionCode, createdBy = currentUserId))
            } catch (_: DuplicateKeyException) {
                requireNotNull(tripRepository.findByPartyIdAndRegionCode(partyId, regionCode))
            }

    /** 기록은 사진 1장 — 다시 부르면 키워드·코멘트·사진을 통째로 새 값으로 바꾼다. */
    private fun writeRecord(
        tripId: Long,
        currentUserId: Long,
        keyword: TripKeyword,
        image: TripImageInput,
        comment: String?,
    ) {
        val existing = tripRecordRepository.findByTripIdAndServiceUserId(tripId, currentUserId)
        val record =
            tripRecordRepository.save(
                existing?.copy(keyword = keyword, comment = comment)
                    ?: TripRecord(
                        tripId = tripId,
                        serviceUserId = currentUserId,
                        keyword = keyword,
                        comment = comment,
                    ),
            )
        tripImageWriter.setImages(requireNotNull(record.id), listOf(image))
    }
}
