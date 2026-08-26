package udumeoli.tripphoto.party.service

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import udumeoli.tripphoto.common.entity.AuditMetadata
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripImage
import udumeoli.tripphoto.trip.entity.TripKeyword
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripImageRepository
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.service.UserService
import java.time.LocalDate
import java.time.LocalDateTime

class PartyCommandServiceTest {
    private lateinit var partyRepository: PartyRepository
    private lateinit var partyMemberRepository: PartyMemberRepository
    private lateinit var userService: UserService
    private lateinit var tripRepository: TripRepository
    private lateinit var tripRecordRepository: TripRecordRepository
    private lateinit var tripImageRepository: TripImageRepository
    private lateinit var imageService: ImageService
    private lateinit var partyCommandService: PartyCommandService

    private val now = LocalDateTime.of(2026, 7, 17, 16, 30)

    @BeforeEach
    fun setUp() {
        partyRepository = mockk()
        partyMemberRepository = mockk()
        userService = mockk()
        tripRepository = mockk()
        tripRecordRepository = mockk()
        tripImageRepository = mockk()
        imageService = mockk()

        val partyQueryService = PartyQueryService(partyRepository, partyMemberRepository, userService)
        partyCommandService =
            PartyCommandService(
                partyRepository = partyRepository,
                partyMemberRepository = partyMemberRepository,
                userService = userService,
                inviteCodeIssuer = InviteCodeIssuer(partyRepository),
                partyQueryService = partyQueryService,
                tripRepository = tripRepository,
                tripRecordRepository = tripRecordRepository,
                tripImageRepository = tripImageRepository,
                imageService = imageService,
            )
    }

    @Test
    fun `여행팟 생성 시 초대코드와 방장 멤버십을 만든다`() {
        val owner = user(1, "방장")
        val savedPartySlot = slot<Party>()
        val savedMemberSlot = slot<PartyMember>()

        every { userService.getCurrentUser(1L) } returns owner
        every { partyRepository.existsByInviteCode(any()) } returns false
        every { partyRepository.save(capture(savedPartySlot)) } answers {
            savedPartySlot.captured.copy(id = 10, auditMetadata = audit())
        }
        every { partyMemberRepository.save(capture(savedMemberSlot)) } answers {
            savedMemberSlot.captured.copy(id = 100)
        }
        stubPartyPayload(
            partyId = 10,
            memberUserIds = listOf(1),
            users = listOf(owner),
        )

        val result = partyCommandService.createParty(currentUserId = 1, name = "유지정민")

        assertThat(result.name).isEqualTo("유지정민")
        assertThat(result.owner.nickname).isEqualTo("방장")
        assertThat(result.members.map { it.nickname }).containsExactly("방장")
        assertThat(savedPartySlot.captured.inviteCode).hasSize(6)
        assertThat(savedPartySlot.captured.inviteCode).matches("[0-9a-z]+")
        assertThat(savedMemberSlot.captured.partyId).isEqualTo(10)
        assertThat(savedMemberSlot.captured.serviceUserId).isEqualTo(1)
    }

    @Test
    fun `초대코드 저장 중 유니크 인덱스와 충돌하면 도메인 예외를 던진다`() {
        val owner = user(1, "방장")

        every { userService.getCurrentUser(1L) } returns owner
        every { partyRepository.existsByInviteCode(any()) } returns false
        every { partyRepository.save(any<Party>()) } throws DuplicateKeyException("duplicate invite code")

        val thrown =
            catchThrowable {
                partyCommandService.createParty(currentUserId = 1, name = "유지정민")
            }

        assertThat(thrown).isInstanceOf(GraphQlDomainException::class.java)
        assertThat((thrown as GraphQlDomainException).code).isEqualTo(GraphQlErrorCode.INVITE_CODE_CONFLICT)
    }

    @Test
    fun `일반 멤버가 나가면 멤버십만 삭제되고 방장은 그대로다`() {
        val party = party(ownerId = 1)
        val memberRow = PartyMember(id = 200, partyId = 10, serviceUserId = 2, createdAt = now)

        every { partyRepository.findById(10L) } returns java.util.Optional.of(party)
        every { userService.getCurrentUser(2L) } returns user(2, "팟원")
        every { partyMemberRepository.existsByPartyIdAndServiceUserId(10L, 2L) } returns true
        every { tripRepository.findAllByPartyId(10L) } returns emptyList()
        every { partyMemberRepository.findByPartyIdAndServiceUserId(10L, 2L) } returns memberRow
        every { partyMemberRepository.delete(memberRow) } just Runs

        val result = partyCommandService.leaveParty(currentUserId = 2, partyId = 10)

        assertThat(result).isEqualTo(10L)
        verify(exactly = 0) { partyRepository.save(any<Party>()) }
    }

    @Test
    fun `나가는 멤버가 팟에 남긴 기록과 이미지는 영구 삭제되고, 기록이 없어진 여행도 삭제된다`() {
        val party = party(ownerId = 1)
        val memberRow = PartyMember(id = 200, partyId = 10, serviceUserId = 2, createdAt = now)
        val tripWithOnlyMemberRecord = trip(id = 50, partyId = 10)
        val tripWithOtherRecords = trip(id = 51, partyId = 10)
        val memberRecord = tripRecord(id = 500, tripId = 50, serviceUserId = 2)
        val otherRecordSameTrip = tripRecord(id = 600, tripId = 51, serviceUserId = 3)
        val memberRecordOnSharedTrip = tripRecord(id = 601, tripId = 51, serviceUserId = 2)
        val tripImageForMember = TripImage(id = 900, tripRecordId = 500, imageId = 9000)

        every { partyRepository.findById(10L) } returns java.util.Optional.of(party)
        every { userService.getCurrentUser(2L) } returns user(2, "팟원")
        every { partyMemberRepository.existsByPartyIdAndServiceUserId(10L, 2L) } returns true
        every { tripRepository.findAllByPartyId(10L) } returns listOf(tripWithOnlyMemberRecord, tripWithOtherRecords)
        every { tripRecordRepository.findAllByTripIdIn(listOf(50L, 51L)) } returns
            listOf(memberRecord, otherRecordSameTrip, memberRecordOnSharedTrip)
        every { tripImageRepository.findAllByTripRecordIdIn(listOf(500L, 601L)) } returns listOf(tripImageForMember)
        every { tripImageRepository.deleteAll(listOf(tripImageForMember)) } just Runs
        every { imageService.deleteImages(listOf(9000L)) } just Runs
        every { tripRecordRepository.deleteAll(listOf(memberRecord, memberRecordOnSharedTrip)) } just Runs
        every { tripRepository.deleteAll(listOf(tripWithOnlyMemberRecord)) } just Runs
        every { partyMemberRepository.findByPartyIdAndServiceUserId(10L, 2L) } returns memberRow
        every { partyMemberRepository.delete(memberRow) } just Runs

        val result = partyCommandService.leaveParty(currentUserId = 2, partyId = 10)

        assertThat(result).isEqualTo(10L)
        verify { tripImageRepository.deleteAll(listOf(tripImageForMember)) }
        verify { imageService.deleteImages(listOf(9000L)) }
        verify { tripRepository.deleteAll(listOf(tripWithOnlyMemberRecord)) }
    }

    @Test
    fun `방장이 나가면 가장 먼저 참여한 멤버에게 방장이 위임된다`() {
        val party = party(ownerId = 1)
        val ownerRow = PartyMember(id = 100, partyId = 10, serviceUserId = 1, createdAt = now.minusDays(2))
        val earlierMember = PartyMember(id = 200, partyId = 10, serviceUserId = 2, createdAt = now.minusDays(1))
        val laterMember = PartyMember(id = 300, partyId = 10, serviceUserId = 3, createdAt = now)
        val savedPartySlot = slot<Party>()

        every { partyRepository.findById(10L) } returns java.util.Optional.of(party)
        every { userService.getCurrentUser(1L) } returns user(1, "방장")
        every { partyMemberRepository.existsByPartyIdAndServiceUserId(10L, 1L) } returns true
        every { partyMemberRepository.findAllByPartyId(10L) } returns listOf(ownerRow, earlierMember, laterMember)
        every { partyRepository.save(capture(savedPartySlot)) } answers { savedPartySlot.captured }
        every { tripRepository.findAllByPartyId(10L) } returns emptyList()
        every { partyMemberRepository.findByPartyIdAndServiceUserId(10L, 1L) } returns ownerRow
        every { partyMemberRepository.delete(ownerRow) } just Runs

        val result = partyCommandService.leaveParty(currentUserId = 1, partyId = 10)

        assertThat(result).isEqualTo(10L)
        assertThat(savedPartySlot.captured.ownerId).isEqualTo(2L)
    }

    @Test
    fun `혼자 남은 방장이 나가면 팟과 그 안의 모든 여행 데이터가 삭제된다`() {
        val party = party(ownerId = 1)
        val ownerRow = PartyMember(id = 100, partyId = 10, serviceUserId = 1, createdAt = now)
        val trip = trip(id = 50, partyId = 10)
        val record = tripRecord(id = 500, tripId = 50, serviceUserId = 1)
        val tripImage = TripImage(id = 900, tripRecordId = 500, imageId = 9000)

        every { partyRepository.findById(10L) } returns java.util.Optional.of(party)
        every { userService.getCurrentUser(1L) } returns user(1, "방장")
        every { partyMemberRepository.existsByPartyIdAndServiceUserId(10L, 1L) } returns true
        every { partyMemberRepository.findAllByPartyId(10L) } returns listOf(ownerRow)
        every { tripRepository.findAllByPartyId(10L) } returns listOf(trip)
        every { tripRecordRepository.findAllByTripIdIn(listOf(50L)) } returns listOf(record)
        every { tripImageRepository.findAllByTripRecordIdIn(listOf(500L)) } returns listOf(tripImage)
        every { tripImageRepository.deleteAll(listOf(tripImage)) } just Runs
        every { imageService.deleteImages(listOf(9000L)) } just Runs
        every { tripRecordRepository.deleteAll(listOf(record)) } just Runs
        every { tripRepository.deleteAll(listOf(trip)) } just Runs
        every { partyMemberRepository.deleteAll(listOf(ownerRow)) } just Runs
        every { partyRepository.delete(party) } just Runs

        val result = partyCommandService.leaveParty(currentUserId = 1, partyId = 10)

        assertThat(result).isEqualTo(10L)
        verify { partyRepository.delete(party) }
        verify { partyMemberRepository.deleteAll(listOf(ownerRow)) }
        verify { imageService.deleteImages(listOf(9000L)) }
    }

    @Test
    fun `팟장은 다른 멤버가 남아 있어도 팟을 삭제할 수 있고, 모든 멤버십과 데이터가 함께 삭제된다`() {
        val party = party(ownerId = 1)
        val ownerRow = PartyMember(id = 100, partyId = 10, serviceUserId = 1, createdAt = now)
        val memberRow = PartyMember(id = 200, partyId = 10, serviceUserId = 2, createdAt = now)

        every { partyRepository.findById(10L) } returns java.util.Optional.of(party)
        every { userService.getCurrentUser(1L) } returns user(1, "방장")
        every { tripRepository.findAllByPartyId(10L) } returns emptyList()
        every { partyMemberRepository.findAllByPartyId(10L) } returns listOf(ownerRow, memberRow)
        every { partyMemberRepository.deleteAll(listOf(ownerRow, memberRow)) } just Runs
        every { partyRepository.delete(party) } just Runs

        val result = partyCommandService.deleteParty(currentUserId = 1, partyId = 10)

        assertThat(result).isEqualTo(10L)
        verify { partyMemberRepository.deleteAll(listOf(ownerRow, memberRow)) }
        verify { partyRepository.delete(party) }
    }

    private fun party(ownerId: Long): Party =
        Party(id = 10, partyName = "우리 팟", inviteCode = "abc123", ownerId = ownerId, auditMetadata = audit())

    private fun trip(
        id: Long,
        partyId: Long,
    ): Trip =
        Trip(
            id = id,
            partyId = partyId,
            regionCode = "SEOUL",
            keyword = TripKeyword.PHOTO,
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 2),
            auditMetadata = audit(),
        )

    private fun tripRecord(
        id: Long,
        tripId: Long,
        serviceUserId: Long,
    ): TripRecord = TripRecord(id = id, tripId = tripId, serviceUserId = serviceUserId, auditMetadata = audit())

    private fun stubPartyPayload(
        partyId: Long,
        memberUserIds: List<Long>,
        users: List<ServiceUser>,
    ) {
        val usersById = users.associateBy { requireNotNull(it.id) }

        every { partyMemberRepository.findAllByPartyId(partyId) } returns
            memberUserIds.mapIndexed { index, userId ->
                PartyMember(id = index + 1L, partyId = partyId, serviceUserId = userId)
            }
        every { userService.findAllById(any<Iterable<Long>>()) } answers {
            firstArg<Iterable<Long>>().mapNotNull { usersById[it] }
        }
    }

    private fun user(
        id: Long,
        nickname: String,
    ): ServiceUser = ServiceUser(id = id, nickname = nickname, profileImage = 1L)

    private fun audit(): AuditMetadata = AuditMetadata(createdAt = now, updatedAt = now)
}
