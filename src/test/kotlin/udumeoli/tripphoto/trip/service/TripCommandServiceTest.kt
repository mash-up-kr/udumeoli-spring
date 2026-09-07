package udumeoli.tripphoto.trip.service

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
 * 두 뮤테이션 모두 "이 지역에 내 기록을 남긴다"로 수렴한다.
 * 핀은 지역마다 하나뿐이라, 새로 찍을지 이미 있는 걸 쓸지를 서버가 알아서 가른다.
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
        every { tripQueryService.toPayload(ME, any()) } returns mockk<TripPayload>()
        every { tripImageWriter.setImages(any(), any()) } just Runs
    }

    @Test
    fun `아무도 안 찍은 지역이면 핀을 새로 찍고 내 기록을 남긴다`() {
        every { tripRepository.findByPartyIdAndRegionCode(PARTY_ID, GANGNEUNG) } returns null
        every { tripRepository.save(any<Trip>()) } returns pin()
        stubNewRecord()

        tripCommandService.createTrip(currentUserId = ME, input = createInput())

        verify(exactly = 1) { tripRepository.save(any<Trip>()) }
        verify(exactly = 1) { tripImageWriter.setImages(RECORD_ID, any()) }
    }

    @Test
    fun `팟원이 이미 찍은 지역이면 핀을 새로 만들지 않고 기록만 얹는다`() {
        every { tripRepository.findByPartyIdAndRegionCode(PARTY_ID, GANGNEUNG) } returns pin()
        stubNewRecord()

        tripCommandService.createTrip(currentUserId = ME, input = createInput())

        verify(exactly = 0) { tripRepository.save(any<Trip>()) }
        verify(exactly = 1) { tripRecordRepository.save(any()) }
    }

    @Test
    fun `같은 지역을 동시에 찍어 유일키에 걸리면 먼저 찍힌 핀에 얹는다`() {
        // 두 번째 조회가 상대방이 방금 만든 핀을 돌려준다 — 요청이 실패로 끝나면 안 된다.
        every { tripRepository.findByPartyIdAndRegionCode(PARTY_ID, GANGNEUNG) } returnsMany listOf(null, pin())
        every { tripRepository.save(any<Trip>()) } throws DuplicateKeyException("uq_trip_party_region")
        stubNewRecord()

        tripCommandService.createTrip(currentUserId = ME, input = createInput())

        verify(exactly = 1) { tripRecordRepository.save(any()) }
    }

    @Test
    fun `같은 지역에 다시 올리면 키워드와 코멘트가 새 값으로 바뀐다`() {
        val savedRecord = slot<TripRecord>()
        every { tripRepository.findByPartyIdAndRegionCode(PARTY_ID, GANGNEUNG) } returns pin()
        every { tripRecordRepository.findByTripIdAndServiceUserId(TRIP_ID, ME) } returns
            recordOf(TripKeyword.HEALING)
        every { tripRecordRepository.save(capture(savedRecord)) } answers { savedRecord.captured }

        tripCommandService.createTrip(
            currentUserId = ME,
            input = createInput(keyword = TripKeyword.DESSERT, comment = "새 코멘트"),
        )

        assertThat(savedRecord.captured.id).isEqualTo(RECORD_ID)
        assertThat(savedRecord.captured.keyword).isEqualTo(TripKeyword.DESSERT)
        assertThat(savedRecord.captured.comment).isEqualTo("새 코멘트")
    }

    @Test
    fun `코멘트 없이 사진만 올려도 된다`() {
        val savedRecord = slot<TripRecord>()
        every { tripRepository.findByPartyIdAndRegionCode(PARTY_ID, GANGNEUNG) } returns pin()
        every { tripRecordRepository.findByTripIdAndServiceUserId(TRIP_ID, ME) } returns null
        every { tripRecordRepository.save(capture(savedRecord)) } answers { savedRecord.captured.copy(id = RECORD_ID) }

        tripCommandService.createTrip(currentUserId = ME, input = createInput(comment = null))

        assertThat(savedRecord.captured.comment).isNull()
    }

    @Test
    fun `존재하지 않는 지역이면 거절하고 핀을 만들지 않는다`() {
        every { regionRepository.existsByRegionCode("99999") } returns false

        val thrown =
            catchThrowable {
                tripCommandService.createTrip(currentUserId = ME, input = createInput(regionCode = "99999"))
            }

        assertThat(thrown)
            .isInstanceOf(GraphQlDomainException::class.java)
            .extracting { (it as GraphQlDomainException).code }
            .isEqualTo(GraphQlErrorCode.REGION_NOT_FOUND)
        verify(exactly = 0) { tripRepository.save(any<Trip>()) }
    }

    @Test
    fun `recordTrip은 핀의 내 기록을 통째로 교체한다`() {
        val savedRecord = slot<TripRecord>()
        every { tripQueryService.requireTrip(TRIP_ID) } returns pin()
        every { tripRecordRepository.findByTripIdAndServiceUserId(TRIP_ID, ME) } returns
            recordOf(TripKeyword.FOOD)
        every { tripRecordRepository.save(capture(savedRecord)) } answers { savedRecord.captured }

        tripCommandService.recordTrip(
            currentUserId = ME,
            input =
                RecordTripInput(
                    tripId = TRIP_ID,
                    keyword = TripKeyword.PHOTO,
                    image = TripImageInput(imageId = 2L),
                    comment = null,
                ),
        )

        assertThat(savedRecord.captured.keyword).isEqualTo(TripKeyword.PHOTO)
        assertThat(savedRecord.captured.comment).isNull()
        verify(exactly = 1) { tripImageWriter.setImages(RECORD_ID, any()) }
    }

    /** 아직 내 기록이 없는 핀 — 새 기록 행이 저장된다. */
    private fun stubNewRecord() {
        every { tripRecordRepository.findByTripIdAndServiceUserId(TRIP_ID, ME) } returns null
        every { tripRecordRepository.save(any()) } answers { firstArg<TripRecord>().copy(id = RECORD_ID) }
    }

    private fun createInput(
        regionCode: String = GANGNEUNG,
        keyword: TripKeyword = TripKeyword.FOOD,
        comment: String? = "좋았다",
    ): CreateTripInput =
        CreateTripInput(
            partyId = PARTY_ID,
            regionCode = regionCode,
            keyword = keyword,
            image = TripImageInput(imageId = 1L),
            comment = comment,
        )
}

private const val PARTY_ID = 7L
private const val ME = 101L
private const val GANGNEUNG = "32030"
private const val TRIP_ID = 99L
private const val RECORD_ID = 500L

private fun pin(): Trip = Trip(id = TRIP_ID, partyId = PARTY_ID, regionCode = GANGNEUNG)

/** 이미 올려 둔 내 기록 — 덮어쓰기 검증의 "예전 값" 쪽이다. */
private fun recordOf(keyword: TripKeyword): TripRecord =
    TripRecord(
        id = RECORD_ID,
        tripId = TRIP_ID,
        serviceUserId = ME,
        keyword = keyword,
        comment = "예전",
    )
