package udumeoli.tripphoto.trip.service

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.party.service.PartyQueryService
import udumeoli.tripphoto.region.repository.RegionRepository
import udumeoli.tripphoto.trip.dto.CreateTripInput
import udumeoli.tripphoto.trip.dto.TripImageInput
import udumeoli.tripphoto.trip.dto.TripPayload
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripKeyword
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository
import java.time.LocalDate

/**
 * "같은 지역은 팟원 전원이 기록해야 다음 방문을 등록할 수 있다"는 기획 규칙을 지킨다.
 * 완료 판정의 모수는 항상 **현재** 팟 멤버라, 기록하지 않고 떠난 사람은 잠금을 풀어준다.
 */
class TripCommandServiceTest {
    private lateinit var tripRepository: TripRepository
    private lateinit var tripRecordRepository: TripRecordRepository
    private lateinit var regionRepository: RegionRepository
    private lateinit var partyQueryService: PartyQueryService
    private lateinit var tripQueryService: TripQueryService
    private lateinit var tripImageWriter: TripImageWriter
    private lateinit var tripCommandService: TripCommandService

    @BeforeEach
    fun setUp() {
        tripRepository = mockk()
        tripRecordRepository = mockk()
        regionRepository = mockk()
        partyQueryService = mockk()
        tripQueryService = mockk()
        tripImageWriter = mockk()

        tripCommandService =
            TripCommandService(
                tripRepository = tripRepository,
                tripRecordRepository = tripRecordRepository,
                regionRepository = regionRepository,
                partyQueryService = partyQueryService,
                tripQueryService = tripQueryService,
                tripImageWriter = tripImageWriter,
            )

        every { partyQueryService.requireMember(PARTY_ID, ME) } just Runs
        every { regionRepository.existsByRegionCode(any()) } returns true
    }

    @Test
    fun `이 지역 첫 방문이면 이전 여행을 따지지 않고 등록한다`() {
        every { tripRepository.findAllByPartyIdAndRegionCode(PARTY_ID, GANGNEUNG) } returns emptyList()
        stubSuccessfulSave()

        tripCommandService.createTrip(currentUserId = ME, input = createTripInput())

        verify(exactly = 1) { tripRepository.save(any()) }
        verify(exactly = 0) { partyQueryService.memberUserIdsInJoinOrder(any()) }
        verify(exactly = 0) { tripRecordRepository.findAllByTripIdIn(any()) }
    }

    @Test
    fun `같은 지역에 전원이 기록하지 않은 여행이 남아 있으면 거절한다`() {
        stubPreviousTrips(
            memberIds = listOf(ME, MINJUN, GAYEON),
            recordedMemberIds = listOf(ME, MINJUN),
        )

        val thrown = catchThrowable { tripCommandService.createTrip(currentUserId = ME, input = createTripInput()) }

        assertThat(thrown)
            .isInstanceOf(GraphQlDomainException::class.java)
            .extracting { (it as GraphQlDomainException).code }
            .isEqualTo(GraphQlErrorCode.REGION_HAS_INCOMPLETE_TRIP)
        verify(exactly = 0) { tripRepository.save(any()) }
    }

    @Test
    fun `현재 멤버 전원이 기록했으면 같은 지역에 다시 등록할 수 있다`() {
        stubPreviousTrips(
            memberIds = listOf(ME, MINJUN, GAYEON),
            recordedMemberIds = listOf(ME, MINJUN, GAYEON),
        )
        stubSuccessfulSave()

        tripCommandService.createTrip(currentUserId = ME, input = createTripInput())

        verify(exactly = 1) { tripRepository.save(any()) }
    }

    @Test
    fun `기록하지 않은 멤버가 팟을 떠나면 그 여행은 완료로 넘어간다`() {
        // 가연이 기록하지 않은 채 나가 현재 멤버가 둘뿐이다 — 남은 둘은 다시 등록할 수 있어야 한다.
        stubPreviousTrips(
            memberIds = listOf(ME, MINJUN),
            recordedMemberIds = listOf(ME, MINJUN),
        )
        stubSuccessfulSave()

        tripCommandService.createTrip(currentUserId = ME, input = createTripInput())

        verify(exactly = 1) { tripRepository.save(any()) }
    }

    @Test
    fun `강퇴된 멤버의 남은 기록이 있어도 현재 멤버 전원이 기록했으면 등록된다`() {
        // 강퇴는 party_member만 지우고 trip_record는 남긴다 — 그 기록이 판정에 끼어들면 안 된다.
        stubPreviousTrips(
            memberIds = listOf(ME, MINJUN),
            recordedMemberIds = listOf(ME, MINJUN, KICKED),
        )
        stubSuccessfulSave()

        tripCommandService.createTrip(currentUserId = ME, input = createTripInput())

        verify(exactly = 1) { tripRepository.save(any()) }
    }

    @Test
    fun `다른 지역에 미완료 여행이 있어도 이 지역 등록은 막지 않는다`() {
        every { tripRepository.findAllByPartyIdAndRegionCode(PARTY_ID, DONGHAE) } returns emptyList()
        stubSuccessfulSave()

        tripCommandService.createTrip(currentUserId = ME, input = createTripInput(regionCode = DONGHAE))

        verify(exactly = 1) { tripRepository.save(any()) }
        verify(exactly = 0) { tripRepository.findAllByPartyIdAndRegionCode(PARTY_ID, GANGNEUNG) }
    }

    /** 강릉에 여행 1건이 이미 있고, [recordedMemberIds]가 그 여행에 기록을 남긴 상태로 만든다. */
    private fun stubPreviousTrips(
        memberIds: List<Long>,
        recordedMemberIds: List<Long>,
    ) {
        every { tripRepository.findAllByPartyIdAndRegionCode(PARTY_ID, GANGNEUNG) } returns
            listOf(trip(id = PREVIOUS_TRIP_ID, regionCode = GANGNEUNG))
        every { partyQueryService.memberUserIdsInJoinOrder(PARTY_ID) } returns memberIds
        every { tripRecordRepository.findAllByTripIdIn(listOf(PREVIOUS_TRIP_ID)) } returns
            recordedMemberIds.mapIndexed { index, memberId ->
                TripRecord(id = index + 1L, tripId = PREVIOUS_TRIP_ID, serviceUserId = memberId)
            }
    }

    private fun stubSuccessfulSave() {
        val savedTrip = trip(id = NEW_TRIP_ID, regionCode = GANGNEUNG)
        every { tripRepository.save(any()) } returns savedTrip
        every { tripRecordRepository.findByTripIdAndServiceUserId(NEW_TRIP_ID, ME) } returns null
        every { tripRecordRepository.save(any()) } returns
            TripRecord(id = NEW_RECORD_ID, tripId = NEW_TRIP_ID, serviceUserId = ME)
        every { tripImageWriter.setImages(NEW_RECORD_ID, any()) } just Runs
        every { tripQueryService.toPayload(ME, savedTrip) } returns mockk<TripPayload>()
    }

    private fun createTripInput(regionCode: String = GANGNEUNG): CreateTripInput =
        CreateTripInput(
            partyId = PARTY_ID,
            regionCode = regionCode,
            keyword = TripKeyword.FOOD,
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 2),
            image = TripImageInput(imageId = 1L),
        )
}

private const val PARTY_ID = 7L
private const val ME = 101L
private const val MINJUN = 102L
private const val GAYEON = 103L
private const val KICKED = 104L
private const val GANGNEUNG = "32030"
private const val DONGHAE = "32040"
private const val PREVIOUS_TRIP_ID = 1L
private const val NEW_TRIP_ID = 99L
private const val NEW_RECORD_ID = 500L

private fun trip(
    id: Long,
    regionCode: String,
): Trip =
    Trip(
        id = id,
        partyId = PARTY_ID,
        regionCode = regionCode,
        keyword = TripKeyword.FOOD,
        startDate = LocalDate.of(2026, 3, 1),
        endDate = LocalDate.of(2026, 3, 1),
    )
