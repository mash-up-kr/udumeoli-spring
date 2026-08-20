package udumeoli.tripphoto.trip.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.trip.dto.MapCellPayload
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripKeyword
import java.time.LocalDate

/**
 * 설계 문서 5장(집계 규칙)의 표와 워크스루를 그대로 고정한다.
 * Spring도 H2도 쓰지 않는다 — aggregate는 순수 함수다.
 */
class MapCellAggregationTest {
    @Test
    fun `칸의 대표 키워드는 가장 많이 나온 키워드다`() {
        val trips =
            listOf(
                trip(id = 1, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-03-01"),
                trip(id = 2, regionCode = "32030", keyword = TripKeyword.NATURE, startDate = "2026-03-05"),
                trip(id = 3, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-03-10"),
            )

        val overview = aggregate(trips, memberIdsByTripId = emptyMap(), currentMemberIds = FOUR_MEMBERS)

        assertThat(overview.municipalities.single().keyword).isEqualTo(TripKeyword.FOOD)
    }

    @Test
    fun `키워드 개수가 동률이면 최근에 시작한 여행의 키워드를 쓴다`() {
        val trips =
            listOf(
                trip(id = 1, regionCode = "32030", keyword = TripKeyword.HEALING, startDate = "2026-03-01"),
                trip(id = 2, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-05-10"),
            )

        val overview = aggregate(trips, memberIdsByTripId = emptyMap(), currentMemberIds = FOUR_MEMBERS)

        assertThat(overview.municipalities.single().keyword).isEqualTo(TripKeyword.FOOD)
    }

    @Test
    fun `개수와 시작일까지 같으면 나중에 등록된 여행의 키워드를 쓴다`() {
        val trips =
            listOf(
                trip(id = 9, regionCode = "32030", keyword = TripKeyword.CULTURE, startDate = "2026-03-01"),
                trip(id = 4, regionCode = "32030", keyword = TripKeyword.CITY, startDate = "2026-03-01"),
            )

        val overview = aggregate(trips, memberIdsByTripId = emptyMap(), currentMemberIds = FOUR_MEMBERS)

        assertThat(overview.municipalities.single().keyword).isEqualTo(TripKeyword.CULTURE)
    }

    @Test
    fun `시·도 칸은 코드 앞 2글자로 묶고 서로 다른 시·군·구 수를 센다`() {
        val overview = aggregate(walkthroughTrips(), walkthroughRecords(), currentMemberIds = FOUR_MEMBERS)

        val gangwon = overview.provinces.single { it.regionCode == "32" }
        assertThat(gangwon.regionCount).isEqualTo(3)
        assertThat(gangwon.visitCount).isEqualTo(4)
        assertThat(gangwon.keyword).isEqualTo(TripKeyword.ACTIVITY)
    }

    @Test
    fun `광역시의 2자리 코드는 시·군·구 목록에도 그대로 들어간다`() {
        val trips = listOf(trip(id = 1, regionCode = "11", keyword = TripKeyword.CITY, startDate = "2026-07-25"))

        val overview = aggregate(trips, memberIdsByTripId = emptyMap(), currentMemberIds = FOUR_MEMBERS)

        assertThat(overview.municipalities.single().regionCode).isEqualTo("11")
        assertThat(overview.provinces.single().regionCode).isEqualTo("11")
    }

    @Test
    fun `같은 지역 재방문은 regionCount에 1, visitCount에 2로 셈한다`() {
        val trips =
            listOf(
                trip(id = 1, regionCode = "32030", keyword = TripKeyword.HEALING, startDate = "2026-03-01"),
                trip(id = 2, regionCode = "32030", keyword = TripKeyword.HEALING, startDate = "2026-05-10"),
            )

        val cell =
            aggregate(trips, memberIdsByTripId = emptyMap(), currentMemberIds = FOUR_MEMBERS).municipalities.single()

        assertThat(cell.regionCount).isEqualTo(1)
        assertThat(cell.visitCount).isEqualTo(2)
    }

    @Test
    fun `recordedMemberCount는 여행이 달라도 같은 멤버를 1로 셈한다`() {
        val trips =
            listOf(
                trip(id = 1, regionCode = "32030", keyword = TripKeyword.HEALING, startDate = "2026-03-01"),
                trip(id = 2, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-05-10"),
            )
        val records = mapOf(1L to setOf(101L, 102L), 2L to setOf(101L))

        val cell = aggregate(trips, records, currentMemberIds = FOUR_MEMBERS).municipalities.single()

        assertThat(cell.recordedMemberCount).isEqualTo(2)
    }

    @Test
    fun `현재 멤버가 아닌 기록자는 recordedMemberCount에서 빠지고 memberCount를 넘지 않는다`() {
        val trips = listOf(trip(id = 1, regionCode = "32030", keyword = TripKeyword.HEALING, startDate = "2026-03-01"))
        // 999는 강퇴돼 party_member에서는 지워졌지만, kickMember는 trip_record를 지우지 않아 기록은 남는다.
        val records = mapOf(1L to setOf(101L, 102L, 999L))
        val currentMemberIds = setOf(101L, 102L)

        val overview = aggregate(trips, records, currentMemberIds)
        val cell = overview.municipalities.single()

        assertThat(cell.recordedMemberCount).isEqualTo(2)
        assertThat(cell.recordedMemberCount).isLessThanOrEqualTo(overview.memberCount)
    }

    @Test
    fun `여행이 하나도 없으면 country는 null이고 나머지는 빈 배열이다`() {
        val overview = aggregate(trips = emptyList(), memberIdsByTripId = emptyMap(), currentMemberIds = FOUR_MEMBERS)

        assertThat(overview.country).isNull()
        assertThat(overview.provinces).isEmpty()
        assertThat(overview.municipalities).isEmpty()
        assertThat(overview.memberCount).isEqualTo(4)
    }

    @Test
    fun `워크스루의 세 레벨이 설계 문서의 표와 일치한다`() {
        val overview = aggregate(walkthroughTrips(), walkthroughRecords(), currentMemberIds = FOUR_MEMBERS)

        assertThat(overview.municipalities)
            .containsExactly(
                MapCellPayload("11", TripKeyword.CITY, regionCount = 1, visitCount = 1, recordedMemberCount = 1),
                MapCellPayload("32030", TripKeyword.FOOD, regionCount = 1, visitCount = 2, recordedMemberCount = 2),
                MapCellPayload("32040", TripKeyword.NATURE, regionCount = 1, visitCount = 1, recordedMemberCount = 3),
                MapCellPayload("32410", TripKeyword.ACTIVITY, regionCount = 1, visitCount = 1, recordedMemberCount = 1),
                MapCellPayload("39010", TripKeyword.NATURE, regionCount = 1, visitCount = 1, recordedMemberCount = 2),
            )
        assertThat(overview.provinces)
            .containsExactly(
                MapCellPayload("11", TripKeyword.CITY, regionCount = 1, visitCount = 1, recordedMemberCount = 1),
                MapCellPayload("32", TripKeyword.ACTIVITY, regionCount = 3, visitCount = 4, recordedMemberCount = 3),
                MapCellPayload("39", TripKeyword.NATURE, regionCount = 1, visitCount = 1, recordedMemberCount = 2),
            )
        assertThat(overview.country)
            .isEqualTo(
                MapCellPayload("KR", TripKeyword.NATURE, regionCount = 5, visitCount = 6, recordedMemberCount = 4),
            )
    }

    @Test
    fun `레벨이 달라도 visitCount 합과 regionCount 합은 보존된다`() {
        val overview = aggregate(walkthroughTrips(), walkthroughRecords(), currentMemberIds = FOUR_MEMBERS)
        val country = requireNotNull(overview.country)

        assertThat(country.visitCount)
            .isEqualTo(overview.provinces.sumOf { it.visitCount })
            .isEqualTo(overview.municipalities.sumOf { it.visitCount })
        assertThat(country.regionCount)
            .isEqualTo(overview.provinces.sumOf { it.regionCount })
            .isEqualTo(overview.municipalities.size)
    }
}

/** 워크스루 팟의 현재 멤버 4명. 기록에 등장하는 101~104가 전부 이 안에 있어 교집합이 숫자를 바꾸지 않는다. */
private val FOUR_MEMBERS = setOf(101L, 102L, 103L, 104L)

/** 설계 문서 5장 워크스루 — 팟 7(멤버 4명), 여행 6건. */
private fun walkthroughTrips(): List<Trip> =
    listOf(
        trip(id = 1, regionCode = "32030", keyword = TripKeyword.HEALING, startDate = "2026-03-01"),
        trip(id = 2, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-05-10"),
        trip(id = 3, regionCode = "32040", keyword = TripKeyword.NATURE, startDate = "2026-06-02"),
        trip(id = 4, regionCode = "32410", keyword = TripKeyword.ACTIVITY, startDate = "2026-07-20"),
        trip(id = 5, regionCode = "11", keyword = TripKeyword.CITY, startDate = "2026-07-25"),
        trip(id = 6, regionCode = "39010", keyword = TripKeyword.NATURE, startDate = "2026-08-01"),
    )

private fun walkthroughRecords(): Map<Long, Set<Long>> =
    mapOf(
        1L to setOf(101L, 102L),
        2L to setOf(101L),
        3L to setOf(101L, 102L, 103L),
        4L to setOf(101L),
        5L to setOf(102L),
        6L to setOf(101L, 104L),
    )

private fun trip(
    id: Long,
    regionCode: String,
    keyword: TripKeyword,
    startDate: String,
): Trip =
    Trip(
        id = id,
        partyId = 7L,
        regionCode = regionCode,
        keyword = keyword,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(startDate),
    )
