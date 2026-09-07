@file:Suppress("LongParameterList", "TooManyFunctions", "ThrowsCount", "MaxLineLength", "MagicNumber", "ForbiddenComment")

package udumeoli.tripphoto.trip

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.image.storage.S3StorageAdapter
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.trip.repository.TripImageRepository
import udumeoli.tripphoto.trip.repository.TripRecordRepository
import udumeoli.tripphoto.trip.repository.TripRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository

/**
 * 스키마에 선언된 Query/Mutation을 실제 문서로 한 번씩 태워 본다.
 * 필드 이름·인자 타입·nullability가 코드와 어긋나면 여기서 깨진다.
 */
@SpringBootTest
@ActiveProfiles("local")
class TripGraphQlSchemaSmokeTest {
    @Autowired lateinit var graphQlService: ExecutionGraphQlService

    @Autowired lateinit var serviceUserRepository: ServiceUserRepository

    @Autowired lateinit var partyRepository: PartyRepository

    @Autowired lateinit var partyMemberRepository: PartyMemberRepository

    @Autowired lateinit var tripRepository: TripRepository

    @Autowired lateinit var tripRecordRepository: TripRecordRepository

    @Autowired lateinit var tripImageRepository: TripImageRepository

    @Autowired lateinit var imageRepository: ImageRepository

    @MockitoBean lateinit var storageAdapter: S3StorageAdapter

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        tripImageRepository.deleteAll()
        tripRecordRepository.deleteAll()
        tripRepository.deleteAll()
        imageRepository.deleteAll()
        partyMemberRepository.deleteAll()
        partyRepository.deleteAll()
        serviceUserRepository.deleteAll()
        Mockito
            .`when`(storageAdapter.publicUrl(anyString()))
            .thenReturn("https://cdn.example.com/original/a.jpg")
        Mockito
            .`when`(
                storageAdapter.createUploadUrl(anyString(), anyString(), org.mockito.Mockito.any(ByteArray::class.java) ?: ByteArray(0)),
            ).thenReturn("https://upload.example.com/a.jpg?sig=1")
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `me와 updateProfile이 User 필드를 그대로 내려준다`() {
        val user = createUser("방장")

        graphQlTester(user)
            .document("query { me { id nickname profileImage } }")
            .execute()
            .path("me.nickname")
            .entity(String::class.java)
            .isEqualTo("방장")

        graphQlTester(user)
            .document(
                """
                mutation {
                  updateProfile(input: { nickname: "방장2", profileImage: 3 }) {
                    id
                    nickname
                    profileImage
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("updateProfile.profileImage")
            .entity(Int::class.java)
            .isEqualTo(3)
    }

    @Test
    fun `팟 조회와 참여 미리보기가 스키마대로 응답한다`() {
        val owner = createUser("방장")
        val member = createUser("멤버")
        val inviteCode = createParty(owner, "테스트팟")
        val partyId = partyIdOf(owner)

        graphQlTester(owner)
            .document(
                """
                query {
                  partyDetail(partyId: "$partyId") {
                    id
                    name
                    inviteCode
                    owner { id }
                    members { id nickname profileImage }
                    createdAt
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("partyDetail.id")
            .entity(String::class.java)
            .isEqualTo(partyId)

        graphQlTester(member)
            .document(
                """
                query {
                  partyPreview(inviteCode: "$inviteCode") {
                    name
                    memberCount
                    members { nickname }
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("partyPreview.memberCount")
            .entity(Int::class.java)
            .isEqualTo(1)
    }

    @Test
    fun `createImageUploadUrl이 imageId와 uploadUrl을 내려준다`() {
        val user = createUser("업로더")

        graphQlTester(user)
            .document(
                """
                mutation {
                  createImageUploadUrl(input: { contentType: "image/jpeg" }) {
                    imageId
                    uploadUrl
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("createImageUploadUrl.uploadUrl")
            .entity(String::class.java)
            .isEqualTo("https://upload.example.com/a.jpg?sig=1")
    }

    @Test
    fun `createTrip은 팟 멤버 전원의 기록 행을 내려준다`() {
        val owner = createUser("방장")
        val member = createUser("멤버")
        val partyId = createPartyWith(owner, member)
        val imageId = saveImage(requireNotNull(owner.id))

        graphQlTester(owner)
            .document(createTripDocument(partyId, imageId))
            .execute()
            .path("createTrip.records[0].keyword")
            .entity(String::class.java)
            .isEqualTo("HEALING")
            .path("createTrip.records[1].keyword")
            .valueIsNull()
            .path("createTrip.records[*].recorded")
            .entityList(Boolean::class.java)
            .containsExactly(true, false)
            .path("createTrip.records[0].image.originalUrl")
            .hasValue()
            .path("createTrip.records[1].image")
            .valueIsNull()
    }

    @Test
    fun `recordTrip은 내 기록을 맨 앞에 올린다`() {
        val owner = createUser("방장")
        val member = createUser("멤버")
        val partyId = createPartyWith(owner, member)
        val tripId = createTrip(owner, partyId)
        val memberImageId = saveImage(requireNotNull(member.id))

        graphQlTester(member)
            .document(
                """
                mutation {
                  recordTrip(input: {
                    tripId: "$tripId"
                    keyword: DESSERT
                    image: { imageId: "$memberImageId" }
                    comment: "나도"
                  }) {
                    id
                    records { member { nickname } recorded comment image { id originalUrl } }
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("recordTrip.records[0].member.nickname")
            .entity(String::class.java)
            .isEqualTo("멤버")
            .path("recordTrip.records[*].recorded")
            .entityList(Boolean::class.java)
            .containsExactly(true, true)
    }

    @Test
    fun `여행 목록 쿼리가 스키마대로 응답한다`() {
        val owner = createUser("방장")
        val partyId = createPartyWith(owner)
        createTrip(owner, partyId)

        graphQlTester(owner)
            .document(
                """
                query {
                  partyTrips(partyId: "$partyId") {
                    id
                    regionCode
                    createdAt
                    records { recorded keyword image { thumbnailUrl } }
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("partyTrips")
            .entityList(Any::class.java)
            .hasSize(1)

        graphQlTester(owner)
            .document(
                """
                query {
                  partyTripInRegion(partyId: "$partyId", regionCode: "11") { id regionCode }
                }
                """.trimIndent(),
            ).execute()
            .path("partyTripInRegion.regionCode")
            .entity(String::class.java)
            .isEqualTo("11")
    }

    @Test
    fun `지역 카드와 여행 통계 쿼리가 스키마대로 응답한다`() {
        val owner = createUser("방장")
        val partyId = createPartyWith(owner)
        createTrip(owner, partyId)

        graphQlTester(owner)
            .document(
                """
                query {
                  partyVisitedRegions(partyId: "$partyId") {
                    regionCode
                    memberCount
                    recordedMemberCount
                    hasUnrecordedTrip
                    slots {
                      member { id nickname }
                      image { id originalUrl thumbnailUrl uploader { nickname } createdAt }
                    }
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("partyVisitedRegions[0].memberCount")
            .entity(Int::class.java)
            .isEqualTo(1)
            .path("partyVisitedRegions[0].recordedMemberCount")
            .entity(Int::class.java)
            .isEqualTo(1)
            .path("partyVisitedRegions[0].slots")
            .entityList(Object::class.java)
            .hasSize(1)
            .path("partyVisitedRegions[0].hasUnrecordedTrip")
            .entity(Boolean::class.java)
            .isEqualTo(false)

        graphQlTester(owner)
            .document(
                """
                query {
                  partyTripStats(partyId: "$partyId") {
                    regionCount
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("partyTripStats.regionCount")
            .entity(Int::class.java)
            .isEqualTo(1)
    }

    @Test
    fun `팟 지도 쿼리가 스키마대로 응답한다`() {
        val owner = createUser("방장")
        val partyId = createPartyWith(owner)
        createTrip(owner, partyId)

        graphQlTester(owner)
            .document(
                """
                query {
                  partyMapOverview(partyId: "$partyId") {
                    memberCount
                    country { regionCode keyword regionCount recordedMemberCount }
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("partyMapOverview.memberCount")
            .entity(Int::class.java)
            .isEqualTo(1)
    }

    @Test
    fun `deleteTripRecord는 마지막 기록이면 null을 반환한다`() {
        val owner = createUser("방장")
        val member = createUser("멤버")
        val partyId = createPartyWith(owner, member)
        val tripId = createTrip(owner, partyId)
        val memberImageId = saveImage(requireNotNull(member.id))
        graphQlTester(member)
            .document(
                """
                mutation {
                  recordTrip(input: { tripId: "$tripId", keyword: PHOTO, image: { imageId: "$memberImageId" } }) { id }
                }
                """.trimIndent(),
            ).execute()
            .path("recordTrip.id")
            .hasValue()

        // 내 기록은 지워졌지만 "나 최상단" 규칙대로 placeholder 행이 맨 앞에 남는다
        graphQlTester(owner)
            .document("""mutation { deleteTripRecord(tripId: "$tripId") { id records { recorded } } }""")
            .execute()
            .path("deleteTripRecord.records[*].recorded")
            .entityList(Boolean::class.java)
            .containsExactly(false, true)

        graphQlTester(member)
            .document("""mutation { deleteTripRecord(tripId: "$tripId") { id } }""")
            .execute()
            .path("deleteTripRecord")
            .valueIsNull()
    }

    @Test
    fun `팟 관리 뮤테이션이 모두 호출된다`() {
        val owner = createUser("방장")
        val member = createUser("멤버")
        val kicked = createUser("강퇴대상")
        val partyId = createPartyWith(owner, member, kicked)

        graphQlTester(owner)
            .document(
                """
                mutation {
                  kickMember(input: { partyId: "$partyId", targetUserId: "${kicked.id}" }) {
                    members { nickname }
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("kickMember.members")
            .entityList(Any::class.java)
            .hasSize(2)

        graphQlTester(owner)
            .document("""mutation { regenerateInviteCode(partyId: "$partyId") { inviteCode } }""")
            .execute()
            .path("regenerateInviteCode.inviteCode")
            .entity(String::class.java)
            .satisfies { code -> check(code.length == 6) }

        graphQlTester(member)
            .document("""mutation { leaveParty(partyId: "$partyId") }""")
            .execute()
            .path("leaveParty")
            .entity(String::class.java)
            .isEqualTo(partyId)

        graphQlTester(owner)
            .document("""mutation { deleteParty(partyId: "$partyId") }""")
            .execute()
            .path("deleteParty")
            .entity(String::class.java)
            .isEqualTo(partyId)
    }

    @Test
    fun `지역 카드는 팟 멤버 전원에게 가입 순서대로 자리를 준다`() {
        val owner = createUser("방장")
        val second = createUser("팟원2")
        val third = createUser("팟원3")
        val partyId = createPartyWith(owner, second, third)
        val tripId = createTrip(owner, partyId)
        recordTrip(second, tripId, takenAt = "2026-07-02")

        // 방장·팟원2는 올렸고 팟원3은 아직이다 — 자리는 셋 다 나오고, 팟원3 자리만 비어 있어야 한다.
        graphQlTester(owner)
            .document(visitedRegionsDocument(partyId))
            .execute()
            .path("partyVisitedRegions[0].memberCount")
            .entity(Int::class.java)
            .isEqualTo(3)
            .path("partyVisitedRegions[0].recordedMemberCount")
            .entity(Int::class.java)
            .isEqualTo(2)
            .path("partyVisitedRegions[0].slots[*].member.nickname")
            .entityList(String::class.java)
            .containsExactly("방장", "팟원2", "팟원3")
            .path("partyVisitedRegions[0].slots[2].image")
            .valueIsNull()
    }

    @Test
    fun `같은 지역에 다시 올리면 내 자리 사진이 교체된다`() {
        val owner = createUser("방장")
        val partyId = createPartyWith(owner)
        createTrip(owner, partyId)

        // 핀은 지역마다 하나라 다시 올려도 새 핀이 생기지 않고 내 기록만 새 값으로 바뀐다.
        val secondVisitImageId = saveImage(requireNotNull(owner.id))
        graphQlTester(owner)
            .document(
                """
                mutation {
                  createTrip(input: {
                    partyId: "$partyId"
                    regionCode: "11"
                    keyword: FOOD
                    image: { imageId: "$secondVisitImageId", takenAt: "2026-09-01" }
                  }) { id }
                }
                """.trimIndent(),
            ).execute()
            .path("createTrip.id")
            .hasValue()

        graphQlTester(owner)
            .document(visitedRegionsDocument(partyId))
            .execute()
            .path("partyVisitedRegions")
            .entityList(Any::class.java)
            .hasSize(1)
            .path("partyVisitedRegions[0].slots")
            .entityList(Any::class.java)
            .hasSize(1)
            .path("partyVisitedRegions[0].slots[0].image.id")
            .entity(String::class.java)
            .isEqualTo(secondVisitImageId.toString())
    }

    private fun visitedRegionsDocument(partyId: String): String =
        """
        query {
          partyVisitedRegions(partyId: "$partyId") {
            regionCode
            memberCount
            recordedMemberCount
            hasUnrecordedTrip
            slots { member { id nickname } image { id } }
          }
        }
        """.trimIndent()

    private fun recordTrip(
        member: ServiceUser,
        tripId: String,
        takenAt: String,
    ) {
        val imageId = saveImage(requireNotNull(member.id))
        graphQlTester(member)
            .document(
                """
                mutation {
                  recordTrip(input: {
                    tripId: "$tripId"
                    keyword: HEALING
                    image: { imageId: "$imageId", takenAt: "$takenAt" }
                    comment: "나도 다녀옴"
                  }) { id }
                }
                """.trimIndent(),
            ).execute()
            .path("recordTrip.id")
            .hasValue()
    }

    private fun createTripDocument(
        partyId: String,
        imageId: Long,
    ): String =
        """
        mutation {
          createTrip(input: {
            partyId: "$partyId"
            regionCode: "11"
            keyword: HEALING
            image: { imageId: "$imageId", takenAt: "2026-07-02" }
            comment: "좋았다"
          }) {
            id
            regionCode
            createdAt
            records {
              member { id nickname profileImage }
              recorded
              keyword
              comment
              image { id originalUrl thumbnailUrl uploader { id } createdAt }
            }
          }
        }
        """.trimIndent()

    private fun createTrip(
        owner: ServiceUser,
        partyId: String,
    ): String =
        graphQlTester(owner)
            .document(createTripDocument(partyId, saveImage(requireNotNull(owner.id))))
            .execute()
            .path("createTrip.id")
            .entity(String::class.java)
            .get()

    private fun createPartyWith(
        owner: ServiceUser,
        vararg members: ServiceUser,
    ): String {
        val inviteCode = createParty(owner, "테스트팟")
        members.forEach { member ->
            graphQlTester(member)
                .document("""mutation { joinParty(inviteCode: "$inviteCode") { id } }""")
                .execute()
                .path("joinParty.id")
                .hasValue()
        }
        return partyIdOf(owner)
    }

    private fun createParty(
        owner: ServiceUser,
        name: String,
    ): String =
        graphQlTester(owner)
            .document(
                """
                mutation {
                  createParty(name: "$name") {
                    id
                    name
                    inviteCode
                    owner { id }
                    members { id }
                    createdAt
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("createParty.inviteCode")
            .entity(String::class.java)
            .get()

    private fun partyIdOf(user: ServiceUser): String =
        graphQlTester(user)
            .document("query { myParties { id name inviteCode owner { id } members { nickname } createdAt } }")
            .execute()
            .path("myParties[0].id")
            .entity(String::class.java)
            .get()

    private fun saveImage(uploaderId: Long): Long =
        requireNotNull(
            imageRepository
                .save(
                    Image(
                        objectKey = "original/$uploaderId-${System.nanoTime()}.jpg",
                        originalUrl = "https://cdn.example.com/original/$uploaderId.jpg",
                        uploaderId = uploaderId,
                    ),
                ).id,
        )

    private fun createUser(nickname: String): ServiceUser = serviceUserRepository.save(ServiceUser(nickname = nickname, profileImage = 1L))

    private fun graphQlTester(user: ServiceUser): ExecutionGraphQlServiceTester {
        val userId = requireNotNull(user.id)
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken(userId.toString(), null, emptyList())
        return ExecutionGraphQlServiceTester
            .builder(graphQlService)
            .build()
    }
}
