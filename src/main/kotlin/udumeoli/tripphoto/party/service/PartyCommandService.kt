package udumeoli.tripphoto.party.service

import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.party.dto.PartyPayload
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripImageRepository
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.service.UserService

// 팟에 걸 수 있는 명령(생성·이름변경·초대코드·나가기·삭제·강퇴)을 한곳에 모아 둔 결과라 함수가 많다.
// 쪼개려면 팟 도메인 전체를 다시 나눠야 해서, 여기서는 임계값만 예외로 둔다.
@Suppress("TooManyFunctions")
@Service
class PartyCommandService(
    private val partyRepository: PartyRepository,
    private val partyMemberRepository: PartyMemberRepository,
    private val userService: UserService,
    private val inviteCodeIssuer: InviteCodeIssuer,
    private val partyQueryService: PartyQueryService,
    private val tripRepository: TripRepository,
    private val tripRecordRepository: TripRecordRepository,
    private val tripImageRepository: TripImageRepository,
    private val imageService: ImageService,
) {
    @Transactional
    fun createParty(
        currentUserId: Long,
        name: String,
    ): PartyPayload {
        userService.getCurrentUser(currentUserId)
        validateNonEmpty(name, "여행팟 이름을 입력해주세요.")

        val party =
            saveParty(
                Party(
                    partyName = name,
                    inviteCode = inviteCodeIssuer.issue(),
                    ownerId = currentUserId,
                ),
            )

        partyMemberRepository.save(
            PartyMember(
                partyId = requireNotNull(party.id),
                serviceUserId = currentUserId,
            ),
        )

        return partyQueryService.toPayload(party)
    }

    /**
     * 팟 이름 변경. 이름 규칙은 생성 때와 같다 — 1자 이상이면 되고 공백·중복·특수기호를 가리지 않는다.
     *
     * [정책] owner 전용이 아니라 멤버 누구나 바꿀 수 있다. 기획이 팟장에게만 열어둔 건
     * 팟 삭제·강퇴·초대코드 재발급이고, 이름 수정에는 그런 단서가 없다.
     *
     * 저장에 [saveParty]를 쓰지 않는 건 이름에는 유니크 제약이 없어서다 —
     * 여기서 DuplicateKeyException이 날 일이 없는데 초대코드 충돌로 둔갑시킬 이유가 없다.
     */
    @Transactional
    fun renameParty(
        currentUserId: Long,
        partyId: Long,
        name: String,
    ): PartyPayload {
        val party =
            partyRepository.findById(partyId).orElseThrow {
                GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
            }
        partyQueryService.requireMember(partyId, currentUserId)
        validateNonEmpty(name, "여행팟 이름을 입력해주세요.")

        return partyQueryService.toPayload(partyRepository.save(party.copy(partyName = name)))
    }

    /** 초대코드 재발급 (owner 전용). 기존 코드는 이 시점부터 무효다. */
    @Transactional
    fun regenerateInviteCode(
        currentUserId: Long,
        partyId: Long,
    ): PartyPayload {
        val party =
            partyRepository.findById(partyId).orElseThrow {
                GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
            }
        requireOwner(party, currentUserId)

        val savedParty = saveParty(party.copy(inviteCode = inviteCodeIssuer.issue()))
        return partyQueryService.toPayload(savedParty)
    }

    /**
     * 나가는 멤버가 이 팟에 남긴 기록(사진/코멘트)은 서버에서 영구 삭제된다.
     * 방장이 나가는 경우, 남은 멤버 중 가장 먼저 참여한 사람에게 방장을 자동으로 위임한다.
     * 혼자 남은 팟이라 위임할 사람이 없으면 [deleteParty]와 동일하게 팟과 데이터를 삭제한다.
     */
    @Transactional
    fun leaveParty(
        currentUserId: Long,
        partyId: Long,
    ): Long {
        val party =
            partyRepository.findById(partyId).orElseThrow {
                GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
            }
        partyQueryService.requireMember(partyId, currentUserId)

        if (party.isOwner(currentUserId)) {
            val successorId = partyQueryService.memberUserIdsInJoinOrder(partyId).firstOrNull { it != currentUserId }
            if (successorId == null) {
                return deleteParty(currentUserId, partyId)
            }
            saveParty(party.copy(ownerId = successorId))
        }

        deleteMemberTripData(partyId, currentUserId)

        val member =
            partyMemberRepository.findByPartyIdAndServiceUserId(partyId, currentUserId)
                ?: throw GraphQlDomainException(GraphQlErrorCode.MEMBER_NOT_FOUND, "멤버를 찾을 수 없습니다.")
        partyMemberRepository.delete(member)
        return partyId
    }

    /**
     * 팟장이 팟과 그 안의 모든 데이터(멤버십/여행/기록/이미지)를 영구 삭제한다.
     * 다른 멤버가 남아 있어도 삭제할 수 있다. 초대코드는 팟 삭제와 함께 즉시 만료된다.
     */
    @Transactional
    fun deleteParty(
        currentUserId: Long,
        partyId: Long,
    ): Long {
        val party =
            partyRepository.findById(partyId).orElseThrow {
                GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
            }
        requireOwner(party, currentUserId)

        deleteAllTripData(partyId)
        partyMemberRepository.deleteAll(partyMemberRepository.findAllByPartyId(partyId))
        partyRepository.delete(party)
        return partyId
    }

    @Transactional
    fun kickMember(
        currentUserId: Long,
        partyId: Long,
        targetUserId: Long,
    ): PartyPayload {
        val party =
            partyRepository.findById(partyId).orElseThrow {
                GraphQlDomainException(GraphQlErrorCode.PARTY_NOT_FOUND, "여행팟을 찾을 수 없습니다.")
            }
        requireOwner(party, currentUserId)

        if (!party.canKick(targetUserId)) {
            throw GraphQlDomainException(
                GraphQlErrorCode.CANNOT_REMOVE_OWNER,
                "방장은 강퇴할 수 없습니다.",
            )
        }

        val member =
            partyMemberRepository.findByPartyIdAndServiceUserId(partyId, targetUserId)
                ?: throw GraphQlDomainException(GraphQlErrorCode.MEMBER_NOT_FOUND, "멤버를 찾을 수 없습니다.")
        partyMemberRepository.delete(member)
        return partyQueryService.toPayload(party)
    }

    private fun requireOwner(
        party: Party,
        userId: Long,
    ) {
        userService.getCurrentUser(userId)
        if (!party.isOwner(userId)) {
            throw GraphQlDomainException(GraphQlErrorCode.FORBIDDEN, "방장만 수행할 수 있습니다.")
        }
    }

    private fun validateNonEmpty(
        value: String,
        message: String,
    ) {
        if (value.isEmpty()) {
            throw GraphQlDomainException(GraphQlErrorCode.VALIDATION_ERROR, message)
        }
    }

    /** 팟에 속한 모든 여행의 기록·이미지를 삭제하고, 여행 자체도 삭제한다. */
    private fun deleteAllTripData(partyId: Long) {
        val trips = tripRepository.findAllByPartyId(partyId)
        val tripIds = trips.mapNotNull { it.id }
        if (tripIds.isEmpty()) {
            return
        }

        deleteTripRecordsWithImages(tripRecordRepository.findAllByTripIdIn(tripIds))
        tripRepository.deleteAll(trips)
    }

    /** 특정 멤버가 이 팟의 여행에 남긴 기록·이미지만 삭제한다. 기록이 없어진 여행은 함께 삭제한다. */
    private fun deleteMemberTripData(
        partyId: Long,
        userId: Long,
    ) {
        val trips = tripRepository.findAllByPartyId(partyId)
        val tripIds = trips.mapNotNull { it.id }
        if (tripIds.isEmpty()) {
            return
        }

        val allRecords = tripRecordRepository.findAllByTripIdIn(tripIds)
        val (memberRecords, remainingRecords) = allRecords.partition { it.serviceUserId == userId }
        if (memberRecords.isEmpty()) {
            return
        }

        deleteTripRecordsWithImages(memberRecords)

        val nonEmptyTripIds = remainingRecords.map { it.tripId }.toSet()
        val emptiedTrips = trips.filter { it.id !in nonEmptyTripIds }
        if (emptiedTrips.isNotEmpty()) {
            tripRepository.deleteAll(emptiedTrips)
        }
    }

    private fun deleteTripRecordsWithImages(records: List<TripRecord>) {
        if (records.isEmpty()) {
            return
        }
        val recordIds = records.mapNotNull { it.id }
        val tripImages = tripImageRepository.findAllByTripRecordIdIn(recordIds)
        tripImageRepository.deleteAll(tripImages)
        imageService.deleteImages(tripImages.map { it.imageId })
        tripRecordRepository.deleteAll(records)
    }

    /** 초대코드는 유니크라 발급/재발급 모두 충돌할 수 있다. 두 경로가 같은 코드로 응답하도록 여기서 잡는다. */
    private fun saveParty(party: Party): Party =
        try {
            partyRepository.save(party)
        } catch (_: DuplicateKeyException) {
            throw GraphQlDomainException(
                GraphQlErrorCode.INVITE_CODE_CONFLICT,
                "초대코드 생성 중 충돌이 발생했습니다. 다시 시도해주세요.",
            )
        }
}
