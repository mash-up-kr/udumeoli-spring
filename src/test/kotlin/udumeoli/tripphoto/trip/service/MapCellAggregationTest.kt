package udumeoli.tripphoto.trip.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.common.entity.AuditMetadata
import udumeoli.tripphoto.trip.dto.MapCellPayload
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripKeyword
import udumeoli.tripphoto.trip.entity.TripRecord
import java.time.LocalDateTime

/**
 * 설계 문서 5장(집계 규칙)의 표와 워크스루를 그대로 고정한다.
 * Spring도 H2도 쓰지 않는다 — aggregate는 순수 함수다.
 *
 * 지역마다 핀은 하나뿐이라, 한 칸에서 키워드가 여럿 나오는 건 "여러 번 다녀와서"가 아니라
 * "같은 지역에 여러 명이 서로 다른 키워드로 올려서"다.
 */
class MapCellAggregationTest {
    @Test
    fun `칸의 대표 키워드는 가장 많이 나온 키워드다`() {
        val trips = listOf(pin(id = 1, regionCode = "32030"))
        val records =
            recordsOf(
                1L to listOf(ME to TripKeyword.FOOD, 102L to TripKeyword.DESSERT, 103L to TripKeyword.FOOD),
            )

        assertThat(overviewOf(trips, records).municipalities.single().keyword).isEqualTo(TripKeyword.FOOD)
    }

    @Test
    fun `개수가 같으면 이름이 가나다순으로 앞선 키워드를 쓴다`() {
        // 나중에 올린 '사진'이 아니라 이름이 앞서는 '디저트'가 뽑힌다.
        val trips = listOf(pin(id = 1, regionCode = "32030"))
        val records = recordsOf(1L to listOf(ME to TripKeyword.DESSERT, 102L to TripKeyword.PHOTO))

        assertThat(overviewOf(trips, records).municipalities.single().keyword).isEqualTo(TripKeyword.DESSERT)
    }

    @Test
    fun `가나다순 비교는 선언 순서가 아니라 한글 이름을 따른다`() {
        // 선언 순서로는 FOOD(맛집)가 ACTIVITY(액티비티)보다 앞이지만, 가나다순으로도 '맛집' < '액티비티'다.
        // 힐링(ㅎ)이 가장 뒤라 셋이 동률이어도 맛집이 뽑힌다.
        val trips = listOf(pin(id = 1, regionCode = "32030"))
        val records =
            recordsOf(
                1L to listOf(ME to TripKeyword.HEALING, 102L to TripKeyword.ACTIVITY, 103L to TripKeyword.FOOD),
            )

        assertThat(overviewOf(trips, records).municipalities.single().keyword).isEqualTo(TripKeyword.FOOD)
    }

    @Test
    fun `개수가 많은 키워드가 가나다순보다 우선한다`() {
        val trips = listOf(pin(id = 1, regionCode = "32030"))
        val records =
            recordsOf(
                1L to listOf(ME to TripKeyword.HEALING, 102L to TripKeyword.HEALING, 103L to TripKeyword.DESSERT),
            )

        assertThat(overviewOf(trips, records).municipalities.single().keyword).isEqualTo(TripKeyword.HEALING)
    }

    @Test
    fun `시·도 칸은 코드 앞 2글자로 묶고 핀 수를 센다`() {
        val overview = overviewOf(walkthroughTrips(), walkthroughRecords())

        val gangwon = overview.provinces.single { it.regionCode == "32" }
        assertThat(gangwon.regionCount).isEqualTo(3)
        // 강원에 묶인 기록은 디저트 3 · 힐링 1 · 맛집 1 · 액티비티 1 — 최빈인 디저트가 대표다.
        assertThat(gangwon.keyword).isEqualTo(TripKeyword.DESSERT)
    }

    @Test
    fun `광역시의 2자리 코드는 시·군·구 목록에도 그대로 들어간다`() {
        val trips = listOf(pin(id = 1, regionCode = "11"))
        val records = recordsOf(1L to listOf(ME to TripKeyword.PHOTO))

        val overview = overviewOf(trips, records)

        assertThat(overview.municipalities.single().regionCode).isEqualTo("11")
        assertThat(overview.provinces.single().regionCode).isEqualTo("11")
    }

    @Test
    fun `현재 멤버가 아닌 기록자는 recordedMemberCount에서 빠지고 memberCount를 넘지 않는다`() {
        // 999는 강퇴돼 party_member에서는 지워졌지만, kickMember는 trip_record를 지우지 않아 기록은 남는다.
        val trips = listOf(pin(id = 1, regionCode = "32030"))
        val records =
            recordsOf(
                1L to listOf(ME to TripKeyword.FOOD, 102L to TripKeyword.FOOD, 999L to TripKeyword.FOOD),
            )

        val overview = overviewOf(trips, records, members = setOf(ME, 102L))
        val cell = overview.municipalities.single()

        assertThat(cell.recordedMemberCount).isEqualTo(2)
        assertThat(cell.recordedMemberCount).isLessThanOrEqualTo(overview.memberCount)
    }

    @Test
    fun `핀이 하나도 없으면 country는 null이고 나머지는 빈 배열이다`() {
        val overview = overviewOf(trips = emptyList())

        assertThat(overview.country).isNull()
        assertThat(overview.provinces).isEmpty()
        assertThat(overview.municipalities).isEmpty()
    }

    @Test
    fun `칸 안에 내가 안 올린 핀이 하나라도 있으면 회색이다`() {
        // 강릉은 올렸지만 동해는 안 올렸다 — 강원(32) 칸 전체가 회색이 된다.
        val trips = listOf(pin(id = 1, regionCode = "32030"), pin(id = 2, regionCode = "32040"))
        val records =
            recordsOf(
                1L to listOf(ME to TripKeyword.FOOD),
                2L to listOf(102L to TripKeyword.FOOD),
            )

        val overview = overviewOf(trips, records)

        assertThat(overview.municipalities.single { it.regionCode == "32030" }.hasUnrecordedTrip).isFalse()
        assertThat(overview.municipalities.single { it.regionCode == "32040" }.hasUnrecordedTrip).isTrue()
        assertThat(overview.provinces.single().hasUnrecordedTrip).isTrue()
    }

    @Test
    fun `내가 기록했으면 팟원이 아직 안 했어도 회색이 아니다`() {
        // 회색 여부는 "내가 올렸나"만 본다. 남이 안 올린 건 n_N 카운터가 알려준다.
        val trips = listOf(pin(id = 1, regionCode = "32030"))
        val cell = overviewOf(trips, recordsOf(1L to listOf(ME to TripKeyword.FOOD))).municipalities.single()

        assertThat(cell.hasUnrecordedTrip).isFalse()
        assertThat(cell.recordedMemberCount).isEqualTo(1)
        assertThat(cell.recordedMemberCount).isLessThan(FOUR_MEMBERS.size)
    }

    @Test
    fun `보는 사람이 달라지면 같은 칸의 회색 여부도 달라진다`() {
        val trips = listOf(pin(id = 1, regionCode = "32030"))
        val records = recordsOf(1L to listOf(ME to TripKeyword.FOOD))

        assertThat(overviewOf(trips, records, viewer = ME).municipalities.single().hasUnrecordedTrip).isFalse()
        assertThat(overviewOf(trips, records, viewer = 102L).municipalities.single().hasUnrecordedTrip).isTrue()
    }

    @Test
    fun `칸의 latestTripAt은 그 안에서 가장 나중에 찍힌 핀의 시각이다`() {
        val overview = overviewOf(walkthroughTrips(), walkthroughRecords())

        // 강원(32)에 묶인 핀은 1~3번이라 3번이 가장 나중이고, 전국은 5번이 가장 나중이다.
        assertThat(overview.provinces.single { it.regionCode == "32" }.latestTripAt).isEqualTo(registeredAt(3))
        assertThat(requireNotNull(overview.country).latestTripAt).isEqualTo(registeredAt(5))
    }

    @Test
    fun `워크스루의 세 레벨이 설계 문서의 표와 일치한다`() {
        val overview = overviewOf(walkthroughTrips(), walkthroughRecords())

        assertThat(overview.municipalities)
            .containsExactly(
                cell("11", TripKeyword.PHOTO, regions = 1, recorded = 1, unrecorded = true, latest = 4),
                cell("32030", TripKeyword.FOOD, regions = 1, recorded = 2, latest = 1),
                cell("32040", TripKeyword.DESSERT, regions = 1, recorded = 3, latest = 2),
                cell("32410", TripKeyword.ACTIVITY, regions = 1, recorded = 1, latest = 3),
                cell("39010", TripKeyword.DESSERT, regions = 1, recorded = 2, latest = 5),
            )
        assertThat(overview.provinces)
            .containsExactly(
                cell("11", TripKeyword.PHOTO, regions = 1, recorded = 1, unrecorded = true, latest = 4),
                cell("32", TripKeyword.DESSERT, regions = 3, recorded = 3, latest = 3),
                cell("39", TripKeyword.DESSERT, regions = 1, recorded = 2, latest = 5),
            )
        assertThat(overview.country)
            .isEqualTo(
                cell("KR", TripKeyword.DESSERT, regions = 5, recorded = 4, unrecorded = true, latest = 5),
            )
    }

    @Test
    fun `레벨이 달라도 regionCount 합은 보존된다`() {
        val overview = overviewOf(walkthroughTrips(), walkthroughRecords())
        val country = requireNotNull(overview.country)

        assertThat(country.regionCount)
            .isEqualTo(overview.provinces.sumOf { it.regionCount })
            .isEqualTo(overview.municipalities.size)
    }
}

/**
 * 기대 칸 하나. [latest]는 등록 시각을 만든 핀 id다 — 표를 읽을 때 "몇 번 핀이 가장 나중이냐"로 보게 된다.
 * [unrecorded]는 기본이 false다. 워크스루에서 101이 못 채운 칸이 서울 한 곳뿐이라, 예외만 눈에 띄게 두려는 것이다.
 */
@Suppress("LongParameterList")
private fun cell(
    regionCode: String,
    keyword: TripKeyword,
    regions: Int,
    recorded: Int,
    latest: Long,
    unrecorded: Boolean = false,
): MapCellPayload =
    MapCellPayload(
        regionCode = regionCode,
        keyword = keyword,
        regionCount = regions,
        recordedMemberCount = recorded,
        hasUnrecordedTrip = unrecorded,
        latestTripAt = registeredAt(latest),
    )

/** 테스트마다 반복되는 인자를 기본값으로 밀어낸다 — 각 테스트가 바꾸는 값만 드러나게. */
private fun overviewOf(
    trips: List<Trip>,
    records: Map<Long, List<TripRecord>> = emptyMap(),
    members: Set<Long> = FOUR_MEMBERS,
    viewer: Long = ME,
) = aggregate(trips, records, members, viewer)

/** 워크스루 팟의 현재 멤버 4명. 기록에 등장하는 101~104가 전부 이 안에 있어 교집합이 숫자를 바꾸지 않는다. */
private val FOUR_MEMBERS = setOf(101L, 102L, 103L, 104L)

/** 워크스루에서 지도를 보고 있는 사람. */
private const val ME = 101L

/** 설계 문서 5장 워크스루 — 팟 7(멤버 4명), 지역 5곳에 핀 5개. */
private fun walkthroughTrips(): List<Trip> =
    listOf(
        pin(id = 1, regionCode = "32030"),
        pin(id = 2, regionCode = "32040"),
        pin(id = 3, regionCode = "32410"),
        pin(id = 4, regionCode = "11"),
        pin(id = 5, regionCode = "39010"),
    )

/** 서울(4번 핀)만 내가 비워 뒀다 — 회색 처리가 걸리는 유일한 칸이다. */
private fun walkthroughRecords(): Map<Long, List<TripRecord>> =
    recordsOf(
        1L to listOf(ME to TripKeyword.HEALING, 102L to TripKeyword.FOOD),
        2L to listOf(ME to TripKeyword.DESSERT, 102L to TripKeyword.DESSERT, 103L to TripKeyword.DESSERT),
        3L to listOf(ME to TripKeyword.ACTIVITY),
        4L to listOf(102L to TripKeyword.PHOTO),
        5L to listOf(ME to TripKeyword.DESSERT, 104L to TripKeyword.DESSERT),
    )

private fun recordsOf(vararg entries: Pair<Long, List<Pair<Long, TripKeyword>>>): Map<Long, List<TripRecord>> =
    entries.associate { (tripId, members) ->
        tripId to
            members.map { (memberId, keyword) ->
                TripRecord(
                    id = tripId * 1000 + memberId,
                    tripId = tripId,
                    serviceUserId = memberId,
                    keyword = keyword,
                )
            }
    }

private fun pin(
    id: Long,
    regionCode: String,
): Trip =
    Trip(
        id = id,
        partyId = 7L,
        regionCode = regionCode,
        auditMetadata = AuditMetadata(createdAt = registeredAt(id)),
    )

/** id 순서가 곧 등록 순서가 되게 붙인 등록 시각. latestTripAt 기대값을 읽기 쉽게 하려는 것이다. */
private fun registeredAt(tripId: Long): LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(tripId)
