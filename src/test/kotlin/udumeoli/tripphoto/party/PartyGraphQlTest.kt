package udumeoli.tripphoto.party

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester
import org.springframework.test.context.ActiveProfiles
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.config.CurrentUserGraphQlInterceptor
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository

@SpringBootTest
@ActiveProfiles("local")
class PartyGraphQlTest {
    @Autowired lateinit var graphQlService: ExecutionGraphQlService

    @Autowired lateinit var serviceUserRepository: ServiceUserRepository

    @Autowired lateinit var partyRepository: PartyRepository

    @Autowired lateinit var partyMemberRepository: PartyMemberRepository

    @BeforeEach
    fun setUp() {
        partyMemberRepository.deleteAll()
        partyRepository.deleteAll()
        serviceUserRepository.deleteAll()
    }

    @Test
    fun `여행팟 생성 시 방장 멤버십이 생성되고 내 여행팟 목록에 노출된다`() {
        val user = createUser("방장")
        val tester = graphQlTester(user)

        val inviteCode =
            tester
                .document(
                    """
                    mutation {
                      createParty(name: "유지정민") {
                        name
                        inviteCode
                        owner { nickname }
                        members { nickname }
                      }
                    }
                    """.trimIndent(),
                ).execute()
                .path("createParty.name")
                .entity(String::class.java)
                .isEqualTo("유지정민")
                .path("createParty.inviteCode")
                .entity(String::class.java)
                .satisfies { code ->
                    assertThat(code).hasSize(6)
                    assertThat(code).matches("[0-9a-zA-Z]+")
                }.path("createParty.owner.nickname")
                .entity(String::class.java)
                .isEqualTo("방장")
                .path("createParty.members[*].nickname")
                .entityList(String::class.java)
                .containsExactly("방장")
                .path("createParty.inviteCode")
                .entity(String::class.java)
                .get()

        tester
            .document(
                """
                query {
                  myParties {
                    name
                    inviteCode
                    members { nickname }
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("myParties[0].name")
            .entity(String::class.java)
            .isEqualTo("유지정민")
            .path("myParties[0].inviteCode")
            .entity(String::class.java)
            .isEqualTo(inviteCode)
    }

    @Test
    fun `초대코드로 여행팟에 참여하고 이미 참여 중이면 거절된다`() {
        val owner = createUser("방장")
        val member = createUser("멤버")
        val inviteCode = createParty(owner, "우리 팟")

        graphQlTester(member)
            .document(
                """
                mutation {
                  joinParty(inviteCode: "$inviteCode") {
                    name
                    members { nickname }
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("joinParty.name")
            .entity(String::class.java)
            .isEqualTo("우리 팟")
            .path("joinParty.members[*].nickname")
            .entityList(String::class.java)
            .containsExactly("방장", "멤버")

        graphQlTester(member)
            .document(
                """
                mutation {
                  joinParty(inviteCode: "$inviteCode") {
                    id
                  }
                }
                """.trimIndent(),
            ).execute()
            .errors()
            .expect { error -> error.extensions["code"] == GraphQlErrorCode.ALREADY_JOINED_PARTY.name }
            .verify()
    }

    @Test
    fun `여행팟 정원이 6명이면 추가 참여가 거절된다`() {
        val owner = createUser("owner")
        val inviteCode = createParty(owner, "six")

        (1..5).forEach { index ->
            val member = createUser("member$index")
            graphQlTester(member)
                .document(
                    """
                    mutation {
                      joinParty(inviteCode: "$inviteCode") {
                        id
                      }
                    }
                    """.trimIndent(),
                ).execute()
                .path("joinParty.id")
                .hasValue()
        }

        val seventh = createUser("seventh")
        graphQlTester(seventh)
            .document(
                """
                mutation {
                  joinParty(inviteCode: "$inviteCode") {
                    id
                  }
                }
                """.trimIndent(),
            ).execute()
            .errors()
            .expect { error -> error.extensions["code"] == GraphQlErrorCode.PARTY_FULL.name }
            .verify()

        val party = requireNotNull(partyRepository.findByInviteCode(inviteCode))
        assertThat(partyMemberRepository.countByPartyId(requireNotNull(party.id))).isEqualTo(6)
    }

    @Test
    fun `초대코드 반복 입력은 횟수 제한에 걸린다`() {
        val user = createUser("attacker")
        val tester = graphQlTester(user)

        repeat(10) {
            tester
                .document(
                    """
                    mutation {
                      joinParty(inviteCode: "aaaaa1") {
                        id
                      }
                    }
                    """.trimIndent(),
                ).execute()
                .errors()
                .expect { error -> error.extensions["code"] == GraphQlErrorCode.INVALID_INVITE_CODE.name }
                .verify()
        }

        tester
            .document(
                """
                mutation {
                  joinParty(inviteCode: "aaaaa1") {
                    id
                  }
                }
                """.trimIndent(),
            ).execute()
            .errors()
            .expect { error -> error.extensions["code"] == GraphQlErrorCode.RATE_LIMITED.name }
            .verify()
    }

    @Test
    fun `여행팟 멤버가 아니면 여행팟 상세 조회가 거절된다`() {
        val owner = createUser("owner")
        val outsider = createUser("outsider")
        val inviteCode = createParty(owner, "private")
        val party = requireNotNull(partyRepository.findByInviteCode(inviteCode))

        graphQlTester(outsider)
            .document(
                """
                query {
                  party(partyId: "${party.id}") {
                    id
                  }
                }
                """.trimIndent(),
            ).execute()
            .errors()
            .expect { error -> error.extensions["code"] == GraphQlErrorCode.FORBIDDEN.name }
            .verify()
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
                    inviteCode
                  }
                }
                """.trimIndent(),
            ).execute()
            .path("createParty.inviteCode")
            .entity(String::class.java)
            .get()

    private fun createUser(nickname: String): ServiceUser = serviceUserRepository.save(ServiceUser(nickname = nickname))

    private fun graphQlTester(user: ServiceUser): ExecutionGraphQlServiceTester {
        val userId = requireNotNull(user.id)
        return ExecutionGraphQlServiceTester
            .builder(graphQlService)
            .configureExecutionInput { _, builder ->
                builder
                    .graphQLContext { context ->
                        context.put(CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY, userId)
                    }.build()
            }.build()
    }
}
