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
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripKeyword
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository
import java.time.LocalDate

class PartyMapQueryServiceTest {
    private lateinit var tripRepository: TripRepository
    private lateinit var tripRecordRepository: TripRecordRepository
    private lateinit var partyQueryService: PartyQueryService
    private lateinit var partyMapQueryService: PartyMapQueryService

    @BeforeEach
    fun setUp() {
        tripRepository = mockk()
        tripRecordRepository = mockk()
        partyQueryService = mockk()

        partyMapQueryService =
            PartyMapQueryService(
                tripRepository = tripRepository,
                tripRecordRepository = tripRecordRepository,
                partyQueryService = partyQueryService,
            )
    }

    @Test
    fun `팟 멤버가 아니면 거절하고 여행을 읽지 않는다`() {
        every { partyQueryService.requireMember(7L, 999L) } throws
            GraphQlDomainException(GraphQlErrorCode.FORBIDDEN, "여행팟 멤버만 접근할 수 있습니다.")

        val thrown = catchThrowable { partyMapQueryService.mapOverview(currentUserId = 999L, partyId = 7L) }

        assertThat(thrown)
            .isInstanceOf(GraphQlDomainException::class.java)
            .extracting { (it as GraphQlDomainException).code }
            .isEqualTo(GraphQlErrorCode.FORBIDDEN)
        verify(exactly = 0) { tripRepository.findAllByPartyId(any()) }
    }

    @Test
    fun `여행이 없으면 기록 조회를 생략하고 빈 집계를 준다`() {
        every { partyQueryService.requireMember(7L, 101L) } just Runs
        every { tripRepository.findAllByPartyId(7L) } returns emptyList()
        every { partyQueryService.memberUserIdsInJoinOrder(7L) } returns listOf(101L, 102L, 103L, 104L)

        val overview = partyMapQueryService.mapOverview(currentUserId = 101L, partyId = 7L)

        assertThat(overview.memberCount).isEqualTo(4)
        assertThat(overview.country).isNull()
        assertThat(overview.provinces).isEmpty()
        assertThat(overview.municipalities).isEmpty()
        verify(exactly = 0) { tripRecordRepository.findAllByTripIdIn(any()) }
    }

    @Test
    fun `여행 건수와 무관하게 리포지토리를 각각 한 번씩만 읽는다`() {
        val trips =
            listOf(
                trip(id = 1, regionCode = "32030", keyword = TripKeyword.FOOD),
                trip(id = 2, regionCode = "32040", keyword = TripKeyword.DESSERT),
            )
        every { partyQueryService.requireMember(7L, 101L) } just Runs
        every { tripRepository.findAllByPartyId(7L) } returns trips
        every { partyQueryService.memberUserIdsInJoinOrder(7L) } returns listOf(101L, 102L, 103L, 104L)
        every { tripRecordRepository.findAllByTripIdIn(listOf(1L, 2L)) } returns
            listOf(
                TripRecord(id = 11, tripId = 1L, serviceUserId = 101L),
                TripRecord(id = 12, tripId = 1L, serviceUserId = 102L),
                TripRecord(id = 13, tripId = 2L, serviceUserId = 101L),
            )

        val overview = partyMapQueryService.mapOverview(currentUserId = 101L, partyId = 7L)

        assertThat(overview.municipalities.map { it.regionCode }).containsExactly("32030", "32040")
        assertThat(overview.provinces.single().regionCode).isEqualTo("32")
        assertThat(requireNotNull(overview.country).recordedMemberCount).isEqualTo(2)
        verify(exactly = 1) { tripRepository.findAllByPartyId(7L) }
        verify(exactly = 1) { tripRecordRepository.findAllByTripIdIn(any()) }
        verify(exactly = 1) { partyQueryService.memberUserIdsInJoinOrder(7L) }
    }

    @Test
    fun `여행이 50건이어도 리포지토리를 각각 한 번씩만 읽는다`() {
        val tripCount = 50
        val trips = (1..tripCount).map { n -> trip(id = n.toLong(), regionCode = "32030", keyword = TripKeyword.FOOD) }
        val tripIds = trips.map { requireNotNull(it.id) }
        val records = tripIds.map { tripId -> TripRecord(id = tripId + 1000, tripId = tripId, serviceUserId = 101L) }
        every { partyQueryService.requireMember(7L, 101L) } just Runs
        every { tripRepository.findAllByPartyId(7L) } returns trips
        every { partyQueryService.memberUserIdsInJoinOrder(7L) } returns listOf(101L, 102L, 103L, 104L)
        every { tripRecordRepository.findAllByTripIdIn(tripIds) } returns records

        val overview = partyMapQueryService.mapOverview(currentUserId = 101L, partyId = 7L)

        assertThat(requireNotNull(overview.country).visitCount).isEqualTo(tripCount)
        verify(exactly = 1) { tripRepository.findAllByPartyId(7L) }
        verify(exactly = 1) { tripRecordRepository.findAllByTripIdIn(any()) }
        verify(exactly = 1) { partyQueryService.memberUserIdsInJoinOrder(7L) }
    }
}

private fun trip(
    id: Long,
    regionCode: String,
    keyword: TripKeyword,
): Trip =
    Trip(
        id = id,
        partyId = 7L,
        regionCode = regionCode,
        keyword = keyword,
        startDate = LocalDate.of(2026, 3, 1),
        endDate = LocalDate.of(2026, 3, 1),
    )
