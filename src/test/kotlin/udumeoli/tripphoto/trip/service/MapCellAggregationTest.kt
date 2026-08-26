package udumeoli.tripphoto.trip.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.common.entity.AuditMetadata
import udumeoli.tripphoto.trip.dto.MapCellPayload
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripKeyword
import java.time.LocalDate
import java.time.LocalDateTime

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
                trip(id = 2, regionCode = "32030", keyword = TripKeyword.DESSERT, startDate = "2026-03-05"),
                trip(id = 3, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-03-10"),
            )

        val overview = overviewOf(trips)

        assertThat(overview.municipalities.single().keyword).isEqualTo(TripKeyword.FOOD)
    }

    @Test
    fun `키워드 개수가 동률이면 최근에 시작한 여행의 키워드를 쓴다`() {
        val trips =
            listOf(
                trip(id = 1, regionCode = "32030", keyword = TripKeyword.HEALING, startDate = "2026-03-01"),
                trip(id = 2, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-05-10"),
            )

        val overview = overviewOf(trips)

        assertThat(overview.municipalities.single().keyword).isEqualTo(TripKeyword.FOOD)
    }

    @Test
    fun `개수와 시작일까지 같으면 나중에 등록된 여행의 키워드를 쓴다`() {
        val trips =
            listOf(
                trip(id = 9, regionCode = "32030", keyword = TripKeyword.DESSERT, startDate = "2026-03-01"),
                trip(id = 4, regionCode = "32030", keyword = TripKeyword.PHOTO, startDate = "2026-03-01"),
            )

        val overview = overviewOf(trips)

        assertThat(overview.municipalities.single().keyword).isEqualTo(TripKeyword.DESSERT)
    }

    @Test
    fun `시·도 칸은 코드 앞 2글자로 묶고 서로 다른 시·군·구 수를 센다`() {
        val overview = overviewOf(walkthroughTrips(), walkthroughRecords())

        val gangwon = overview.provinces.single { it.regionCode == "32" }
        assertThat(gangwon.regionCount).isEqualTo(3)
        assertThat(gangwon.visitCount).isEqualTo(4)
        assertThat(gangwon.keyword).isEqualTo(TripKeyword.ACTIVITY)
    }

    @Test
    fun `광역시의 2자리 코드는 시·군·구 목록에도 그대로 들어간다`() {
        val trips = listOf(trip(id = 1, regionCode = "11", keyword = TripKeyword.PHOTO, startDate = "2026-07-25"))

        val overview = overviewOf(trips)

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
            overviewOf(trips).municipalities.single()

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

        val cell = overviewOf(trips, records).municipalities.single()

        assertThat(cell.recordedMemberCount).isEqualTo(2)
    }

    @Test
    fun `현재 멤버가 아닌 기록자는 recordedMemberCount에서 빠지고 memberCount를 넘지 않는다`() {
        val trips = listOf(trip(id = 1, regionCode = "32030", keyword = TripKeyword.HEALING, startDate = "2026-03-01"))
        // 999는 강퇴돼 party_member에서는 지워졌지만, kickMember는 trip_record를 지우지 않아 기록은 남는다.
        val records = mapOf(1L to setOf(101L, 102L, 999L))
        val currentMemberIds = setOf(101L, 102L)

        val overview = overviewOf(trips, records, members = currentMemberIds)
        val cell = overview.municipalities.single()

        assertThat(cell.recordedMemberCount).isEqualTo(2)
        assertThat(cell.recordedMemberCount).isLessThanOrEqualTo(overview.memberCount)
    }

    @Test
    fun `여행이 하나도 없으면 country는 null이고 나머지는 빈 배열이다`() {
        val overview = overviewOf(trips = emptyList())

        assertThat(overview.country).isNull()
        assertThat(overview.provinces).isEmpty()
        assertThat(overview.municipalities).isEmpty()
        assertThat(overview.memberCount).isEqualTo(4)
    }

    @Test
    fun `같은 지역을 두 번 갔는데 한 번만 기록했으면 그 칸은 다시 회색이 된다`() {
        // 기획 확정: 강릉 1차를 내가 올렸어도 2차가 비어 있으면 스티커 없이 회색 + "탭해서 기록하기"다.
        val trips =
            listOf(
                trip(id = 1, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-03-01"),
                trip(id = 2, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-05-10"),
            )
        val records = mapOf(1L to setOf(ME, 102L), 2L to setOf(102L))

        val cell = overviewOf(trips, records).municipalities.single()

        assertThat(cell.hasUnrecordedTrip).isTrue()
        assertThat(cell.recordedMemberCount).isEqualTo(2)
    }

    @Test
    fun `그 지역의 여행을 전부 기록했으면 회색에서 벗어난다`() {
        val trips =
            listOf(
                trip(id = 1, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-03-01"),
                trip(id = 2, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-05-10"),
            )
        val records = mapOf(1L to setOf(ME), 2L to setOf(ME))

        assertThat(overviewOf(trips, records).municipalities.single().hasUnrecordedTrip).isFalse()
    }

    @Test
    fun `내가 기록했으면 팟원이 아직 안 했어도 회색이 아니다`() {
        // 회색 여부는 "내가 올렸나"만 본다. 남이 안 올린 건 n_N 카운터가 알려준다.
        val trips = listOf(trip(id = 1, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-03-01"))
        val cell = overviewOf(trips, mapOf(1L to setOf(ME))).municipalities.single()

        assertThat(cell.hasUnrecordedTrip).isFalse()
        assertThat(cell.recordedMemberCount).isEqualTo(1)
        assertThat(cell.recordedMemberCount).isLessThan(FOUR_MEMBERS.size)
    }

    @Test
    fun `보는 사람이 달라지면 같은 칸의 회색 여부도 달라진다`() {
        val trips = listOf(trip(id = 1, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-03-01"))
        val records = mapOf(1L to setOf(ME))

        assertThat(overviewOf(trips, records, viewer = ME).municipalities.single().hasUnrecordedTrip).isFalse()
        assertThat(overviewOf(trips, records, viewer = 102L).municipalities.single().hasUnrecordedTrip).isTrue()
    }

    @Test
    fun `칸의 latestTripAt은 그 안에서 가장 나중에 등록된 여행의 시각이다`() {
        val overview = overviewOf(walkthroughTrips(), walkthroughRecords())

        // 강원(32)에 묶인 여행은 1~4번이라 4번이 가장 나중이고, 전국은 6번이 가장 나중이다.
        assertThat(overview.provinces.single { it.regionCode == "32" }.latestTripAt).isEqualTo(registeredAt(4))
        assertThat(requireNotNull(overview.country).latestTripAt).isEqualTo(registeredAt(6))
    }

    @Test
    fun `워크스루의 세 레벨이 설계 문서의 표와 일치한다`() {
        val overview = overviewOf(walkthroughTrips(), walkthroughRecords())

        assertThat(overview.municipalities)
            .containsExactly(
                cell("11", TripKeyword.PHOTO, regions = 1, visits = 1, recorded = 1, unrecorded = true, latest = 5),
                cell("32030", TripKeyword.FOOD, regions = 1, visits = 2, recorded = 2, latest = 2),
                cell("32040", TripKeyword.DESSERT, regions = 1, visits = 1, recorded = 3, latest = 3),
                cell("32410", TripKeyword.ACTIVITY, regions = 1, visits = 1, recorded = 1, latest = 4),
                cell("39010", TripKeyword.DESSERT, regions = 1, visits = 1, recorded = 2, latest = 6),
            )
        assertThat(overview.provinces)
            .containsExactly(
                cell("11", TripKeyword.PHOTO, regions = 1, visits = 1, recorded = 1, unrecorded = true, latest = 5),
                cell("32", TripKeyword.ACTIVITY, regions = 3, visits = 4, recorded = 3, latest = 4),
                cell("39", TripKeyword.DESSERT, regions = 1, visits = 1, recorded = 2, latest = 6),
            )
        assertThat(overview.country)
            .isEqualTo(
                cell("KR", TripKeyword.DESSERT, regions = 5, visits = 6, recorded = 4, unrecorded = true, latest = 6),
            )
    }

    @Test
    fun `레벨이 달라도 visitCount 합과 regionCount 합은 보존된다`() {
        val overview = overviewOf(walkthroughTrips(), walkthroughRecords())
        val country = requireNotNull(overview.country)

        assertThat(country.visitCount)
            .isEqualTo(overview.provinces.sumOf { it.visitCount })
            .isEqualTo(overview.municipalities.sumOf { it.visitCount })
        assertThat(country.regionCount)
            .isEqualTo(overview.provinces.sumOf { it.regionCount })
            .isEqualTo(overview.municipalities.size)
    }
}

/**
 * 기대 칸 하나. [latest]는 등록 시각을 만든 trip id다 — 표를 읽을 때 "몇 번 여행이 가장 나중이냐"로 보게 된다.
 * [unrecorded]는 기본이 false다. 워크스루에서 101이 못 채운 칸이 서울 한 곳뿐이라, 예외만 눈에 띄게 두려는 것이다.
 */
@Suppress("LongParameterList")
private fun cell(
    regionCode: String,
    keyword: TripKeyword,
    regions: Int,
    visits: Int,
    recorded: Int,
    latest: Long,
    unrecorded: Boolean = false,
): MapCellPayload =
    MapCellPayload(
        regionCode = regionCode,
        keyword = keyword,
        regionCount = regions,
        visitCount = visits,
        recordedMemberCount = recorded,
        hasUnrecordedTrip = unrecorded,
        latestTripAt = registeredAt(latest),
    )

/** 테스트마다 반복되는 인자를 기본값으로 밀어낸다 — 각 테스트가 바꾸는 값만 드러나게. */
private fun overviewOf(
    trips: List<Trip>,
    records: Map<Long, Set<Long>> = emptyMap(),
    members: Set<Long> = FOUR_MEMBERS,
    viewer: Long = ME,
) = aggregate(trips, records, members, viewer)

/** 워크스루 팟의 현재 멤버 4명. 기록에 등장하는 101~104가 전부 이 안에 있어 교집합이 숫자를 바꾸지 않는다. */
private val FOUR_MEMBERS = setOf(101L, 102L, 103L, 104L)

/** 워크스루에서 지도를 보고 있는 사람. */
private const val ME = 101L

/** 설계 문서 5장 워크스루 — 팟 7(멤버 4명), 여행 6건. */
private fun walkthroughTrips(): List<Trip> =
    listOf(
        trip(id = 1, regionCode = "32030", keyword = TripKeyword.HEALING, startDate = "2026-03-01"),
        trip(id = 2, regionCode = "32030", keyword = TripKeyword.FOOD, startDate = "2026-05-10"),
        trip(id = 3, regionCode = "32040", keyword = TripKeyword.DESSERT, startDate = "2026-06-02"),
        trip(id = 4, regionCode = "32410", keyword = TripKeyword.ACTIVITY, startDate = "2026-07-20"),
        trip(id = 5, regionCode = "11", keyword = TripKeyword.PHOTO, startDate = "2026-07-25"),
        trip(id = 6, regionCode = "39010", keyword = TripKeyword.DESSERT, startDate = "2026-08-01"),
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
        auditMetadata = AuditMetadata(createdAt = registeredAt(id)),
    )

/** id 순서가 곧 등록 순서가 되게 붙인 등록 시각. latestTripAt 기대값을 읽기 쉽게 하려는 것이다. */
private fun registeredAt(tripId: Long): LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(tripId)
