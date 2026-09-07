package udumeoli.tripphoto.trip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.trip.entity.Trip
import udumeoli.tripphoto.trip.entity.TripKeyword
import udumeoli.tripphoto.trip.entity.TripRecord
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository

/** 설계 문서 5장 워크스루를 H2에 그대로 심고 세 레벨 응답을 통째로 검증한다. */
@SpringBootTest
@ActiveProfiles("local")
class PartyMapGraphQlTest {
    @Autowired lateinit var graphQlService: ExecutionGraphQlService

    @Autowired lateinit var serviceUserRepository: ServiceUserRepository

    @Autowired lateinit var partyRepository: PartyRepository

    @Autowired lateinit var partyMemberRepository: PartyMemberRepository

    @Autowired lateinit var tripRepository: TripRepository

    @Autowired lateinit var tripRecordRepository: TripRecordRepository

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        tripRecordRepository.deleteAll()
        tripRepository.deleteAll()
        partyMemberRepository.deleteAll()
        partyRepository.deleteAll()
        serviceUserRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `팟 멤버가 아니면 지도 조회가 거절된다`() {
        val owner = createUser("방장")
        val outsider = createUser("남")
        val partyId = createParty(owner)

        graphQlTester(outsider)
            .document("query { partyMapOverview(partyId: \"$partyId\") { memberCount } }")
            .execute()
            .errors()
            .expect { error -> error.extensions["code"] == GraphQlErrorCode.FORBIDDEN.name }
            .verify()
    }

    @Test
    fun `여행이 없으면 country는 null이고 memberCount만 온다`() {
        val owner = createUser("방장")
        val partyId = createParty(owner)

        graphQlTester(owner)
            .document(OVERVIEW_DOCUMENT.format(partyId))
            .execute()
            .path("partyMapOverview.memberCount")
            .entity(Int::class.java)
            .isEqualTo(1)
            .path("partyMapOverview.country")
            .valueIsNull()
            .path("partyMapOverview.provinces")
            .entityList(Any::class.java)
            .hasSize(0)
            .path("partyMapOverview.municipalities")
            .entityList(Any::class.java)
            .hasSize(0)
    }

    @Test
    fun `워크스루 핀 5개가 세 레벨로 집계돼 내려온다`() {
        val owner = createUser("101")
        val second = createUser("102")
        val third = createUser("103")
        val fourth = createUser("104")
        val partyId = createParty(owner, second, third, fourth)
        seedWalkthrough(partyId, owner, second, third, fourth)

        graphQlTester(owner)
            .document(OVERVIEW_DOCUMENT.format(partyId))
            .execute()
            .path("partyMapOverview.memberCount")
            .entity(Int::class.java)
            .isEqualTo(4)
            .path("partyMapOverview.country.regionCode")
            .entity(String::class.java)
            .isEqualTo("KR")
            .path("partyMapOverview.country.keyword")
            .entity(String::class.java)
            .isEqualTo("DESSERT")
            .path("partyMapOverview.country.regionCount")
            .entity(Int::class.java)
            .isEqualTo(5)
            .path("partyMapOverview.country.recordedMemberCount")
            .entity(Int::class.java)
            .isEqualTo(4)
            .path("partyMapOverview.provinces[*].regionCode")
            .entityList(String::class.java)
            .containsExactly("11", "32", "39")
            // 강원에 묶인 기록은 디저트 3 · 힐링 1 · 맛집 1 · 액티비티 1 — 최빈인 디저트가 대표다.
            .path("partyMapOverview.provinces[1].keyword")
            .entity(String::class.java)
            .isEqualTo("DESSERT")
            .path("partyMapOverview.provinces[1].regionCount")
            .entity(Int::class.java)
            .isEqualTo(3)
            .path("partyMapOverview.provinces[1].recordedMemberCount")
            .entity(Int::class.java)
            .isEqualTo(3)
            .path("partyMapOverview.municipalities[*].regionCode")
            .entityList(String::class.java)
            .containsExactly("11", "32030", "32040", "32410", "39010")
            // 강릉(32030)은 힐링 1 · 맛집 1로 동률이라 가나다순 앞선 맛집이 대표다.
            .path("partyMapOverview.municipalities[1].keyword")
            .entity(String::class.java)
            .isEqualTo("FOOD")
            .path("partyMapOverview.municipalities[1].regionCount")
            .entity(Int::class.java)
            .isEqualTo(1)
            .path("partyMapOverview.municipalities[2].recordedMemberCount")
            .entity(Int::class.java)
            .isEqualTo(3)
    }

    @Test
    fun `내가 못 채운 지역만 회색으로 내려온다`() {
        val owner = createUser("방장")
        val second = createUser("팟원2")
        val third = createUser("팟원3")
        val fourth = createUser("팟원4")
        val partyId = createParty(owner, second, third, fourth)
        seedWalkthrough(partyId, owner, second, third, fourth)

        // 방장은 서울(11) 핀에만 사진을 안 올렸다.
        graphQlTester(owner)
            .document(OVERVIEW_DOCUMENT.format(partyId))
            .execute()
            .path("partyMapOverview.municipalities[?(@.regionCode == '11')].hasUnrecordedTrip")
            .entityList(Boolean::class.java)
            .containsExactly(true)
            .path("partyMapOverview.municipalities[?(@.regionCode == '32030')].hasUnrecordedTrip")
            .entityList(Boolean::class.java)
            .containsExactly(false)
            .path("partyMapOverview.country.hasUnrecordedTrip")
            .entity(Boolean::class.java)
            .isEqualTo(true)
            .path("partyMapOverview.country.latestTripAt")
            .entity(String::class.java)
            .satisfies { assertThat(it).isNotBlank() }
    }

    @Test
    fun `팟원만 올린 지역은 내 눈에 회색으로 내려온다`() {
        val owner = createUser("방장")
        val member = createUser("팟원")
        val partyId = createParty(owner, member)
        val gangneung = saveTrip(partyId, "32030")
        saveRecords(gangneung, TripKeyword.FOOD, member)

        graphQlTester(owner)
            .document(OVERVIEW_DOCUMENT.format(partyId))
            .execute()
            .path("partyMapOverview.municipalities[0].hasUnrecordedTrip")
            .entity(Boolean::class.java)
            .isEqualTo(true)
            .path("partyMapOverview.municipalities[0].recordedMemberCount")
            .entity(Int::class.java)
            .isEqualTo(1)
    }

    @Test
    fun `강퇴된 멤버의 기록은 recordedMemberCount에 포함되지 않는다`() {
        val owner = createUser("방장")
        val kicked = createUser("강퇴대상")
        val partyId = createParty(owner, kicked)
        val tripId = saveTrip(partyId, "32030")
        saveRecords(tripId, TripKeyword.HEALING, owner, kicked)

        // kickMember는 party_member만 지우고 trip_record는 남긴다 — 그 기록이 세지면 n(2)이 N(1)을 넘는다.
        graphQlTester(owner)
            .document(
                """mutation { kickMember(input: { partyId: "$partyId", targetUserId: "${kicked.id}" }) { id } }""",
            ).execute()
            .path("kickMember.id")
            .hasValue()

        graphQlTester(owner)
            .document(OVERVIEW_DOCUMENT.format(partyId))
            .execute()
            .path("partyMapOverview.memberCount")
            .entity(Int::class.java)
            .isEqualTo(1)
            .path("partyMapOverview.country.recordedMemberCount")
            .entity(Int::class.java)
            .isEqualTo(1)
    }

    private fun seedWalkthrough(
        partyId: Long,
        owner: ServiceUser,
        second: ServiceUser,
        third: ServiceUser,
        fourth: ServiceUser,
    ) {
        val gangneung = saveTrip(partyId, "32030")
        val donghae = saveTrip(partyId, "32040")
        val yangyang = saveTrip(partyId, "32410")
        val seoul = saveTrip(partyId, "11")
        val jeju = saveTrip(partyId, "39010")

        // 강릉만 두 사람이 서로 다른 키워드를 골랐다 — 동률 규칙이 걸리는 유일한 칸이다.
        saveRecords(gangneung, TripKeyword.HEALING, owner)
        saveRecords(gangneung, TripKeyword.FOOD, second)
        saveRecords(donghae, TripKeyword.DESSERT, owner, second, third)
        saveRecords(yangyang, TripKeyword.ACTIVITY, owner)
        // 서울은 방장이 비워 둔다.
        saveRecords(seoul, TripKeyword.PHOTO, second)
        saveRecords(jeju, TripKeyword.DESSERT, owner, fourth)
    }

    private fun saveTrip(
        partyId: Long,
        regionCode: String,
    ): Long =
        requireNotNull(
            tripRepository.save(Trip(partyId = partyId, regionCode = regionCode)).id,
        )

    private fun saveRecords(
        tripId: Long,
        keyword: TripKeyword,
        vararg members: ServiceUser,
    ) {
        members.forEach { member ->
            tripRecordRepository.save(
                TripRecord(tripId = tripId, serviceUserId = requireNotNull(member.id), keyword = keyword),
            )
        }
    }

    private fun createParty(
        owner: ServiceUser,
        vararg members: ServiceUser,
    ): Long {
        val ownerId = requireNotNull(owner.id)
        val party =
            partyRepository.save(
                Party(partyName = "우리 팟", inviteCode = "abc123", ownerId = ownerId),
            )
        val partyId = requireNotNull(party.id)
        (listOf(owner) + members).forEach { member ->
            partyMemberRepository.save(
                PartyMember(partyId = partyId, serviceUserId = requireNotNull(member.id)),
            )
        }
        return partyId
    }

    private fun createUser(nickname: String): ServiceUser {
        val user = ServiceUser(nickname = nickname, profileImage = 1L)
        return serviceUserRepository.save(user)
    }

    private fun graphQlTester(user: ServiceUser): ExecutionGraphQlServiceTester {
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(requireNotNull(user.id).toString(), null, emptyList())
        return ExecutionGraphQlServiceTester.builder(graphQlService).build()
    }

    companion object {
        private val OVERVIEW_DOCUMENT =
            """
            query {
              partyMapOverview(partyId: "%s") {
                memberCount
                country { regionCode keyword regionCount recordedMemberCount hasUnrecordedTrip latestTripAt }
                provinces { regionCode keyword regionCount recordedMemberCount hasUnrecordedTrip }
                municipalities { regionCode keyword regionCount recordedMemberCount hasUnrecordedTrip }
              }
            }
            """.trimIndent()
    }
}
