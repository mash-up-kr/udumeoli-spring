package udumeoli.tripphoto.trip.service

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
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository

/**
 * - createTrip: 새 방문 + 내 기록
 * - recordTrip: 이미 있는 방문에 내 기록 (재호출 시 통째 교체)
 * - deleteTripRecord: 내 기록 삭제, 마지막 기록이면 여행도 함께 정리
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
        val regionCode = input.regionCode
        if (!regionRepository.existsByRegionCode(regionCode)) {
            throw GraphQlDomainException(
                GraphQlErrorCode.REGION_NOT_FOUND,
                "존재하지 않는 지역입니다: ${input.regionCode}",
            )
        }
        if (input.startDate.isAfter(input.endDate)) {
            throw GraphQlDomainException(
                GraphQlErrorCode.VALIDATION_ERROR,
                "여행 시작일은 종료일보다 늦을 수 없습니다.",
            )
        }

        val trip =
            tripRepository.save(
                Trip(
                    partyId = input.partyId,
                    regionCode = regionCode,
                    keyword = input.keyword,
                    startDate = input.startDate,
                    endDate = input.endDate,
                    createdBy = currentUserId,
                ),
            )
        writeRecord(requireNotNull(trip.id), currentUserId, input.image, input.comment)
        return tripQueryService.toPayload(currentUserId, trip)
    }

    @Transactional
    fun recordTrip(
        currentUserId: Long,
        input: RecordTripInput,
    ): TripPayload {
        val trip = tripQueryService.requireTrip(input.tripId)
        partyQueryService.requireMember(trip.partyId, currentUserId)

        writeRecord(input.tripId, currentUserId, input.image, input.comment)
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

    /** 기록은 사진 1장 — 다시 부르면 기존 사진을 새 사진으로 교체한다. */
    private fun writeRecord(
        tripId: Long,
        currentUserId: Long,
        image: TripImageInput,
        comment: String?,
    ) {
        val existing = tripRecordRepository.findByTripIdAndServiceUserId(tripId, currentUserId)
        val record =
            tripRecordRepository.save(
                existing?.copy(comment = comment)
                    ?: TripRecord(tripId = tripId, serviceUserId = currentUserId, comment = comment),
            )
        tripImageWriter.setImages(requireNotNull(record.id), listOf(image))
    }
}
