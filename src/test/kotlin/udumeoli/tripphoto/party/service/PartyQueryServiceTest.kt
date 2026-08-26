package udumeoli.tripphoto.party.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.common.entity.AuditMetadata
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.service.UserService
import java.time.LocalDateTime

class PartyQueryServiceTest {
    private lateinit var partyRepository: PartyRepository
    private lateinit var partyMemberRepository: PartyMemberRepository
    private lateinit var userService: UserService
    private lateinit var partyQueryService: PartyQueryService

    @BeforeEach
    fun setUp() {
        partyRepository = mockk()
        partyMemberRepository = mockk()
        userService = mockk()
        partyQueryService = PartyQueryService(partyRepository, partyMemberRepository, userService)

        every { userService.getCurrentUser(ME) } returns me
        every { userService.findAllById(any()) } returns listOf(me)
    }

    @Test
    fun `내 팟 목록은 가장 최근에 참여한 팟이 맨 위에 온다`() {
        // 리포지토리가 참여 순서(오래된 것부터)로 돌려줘도 응답은 뒤집혀 나와야 한다.
        every { partyMemberRepository.findAllByServiceUserId(ME) } returns
            listOf(
                membership(id = 1, partyId = 10, joinedAt = LocalDateTime.of(2025, 3, 2, 9, 0)),
                membership(id = 2, partyId = 20, joinedAt = LocalDateTime.of(2026, 7, 15, 9, 0)),
                membership(id = 3, partyId = 30, joinedAt = LocalDateTime.of(2026, 8, 26, 9, 0)),
            )
        stubParties(10L to "21학번~", 20L to "강민지원", 30L to "우두머리")

        val myParties = partyQueryService.myParties(ME)

        assertThat(myParties.map { it.name }).containsExactly("우두머리", "강민지원", "21학번~")
    }

    @Test
    fun `같은 시각에 참여했으면 나중에 저장된 멤버십이 위로 온다`() {
        val sameMoment = LocalDateTime.of(2026, 8, 26, 9, 0)
        every { partyMemberRepository.findAllByServiceUserId(ME) } returns
            listOf(
                membership(id = 1, partyId = 10, joinedAt = sameMoment),
                membership(id = 2, partyId = 20, joinedAt = sameMoment),
            )
        stubParties(10L to "먼저", 20L to "나중")

        val myParties = partyQueryService.myParties(ME)

        assertThat(myParties.map { it.name }).containsExactly("나중", "먼저")
    }

    @Test
    fun `소속 팟이 없으면 빈 배열이고 팟을 읽지 않는다`() {
        every { partyMemberRepository.findAllByServiceUserId(ME) } returns emptyList()

        assertThat(partyQueryService.myParties(ME)).isEmpty()
    }

    /** 각 팟을 "나 혼자 있는 팟"으로 세워 toPayload가 필요로 하는 조회를 채운다. */
    private fun stubParties(vararg idToName: Pair<Long, String>) {
        val parties = idToName.map { (id, name) -> party(id = id, name = name) }
        every { partyRepository.findAllById(any<Iterable<Long>>()) } returns parties
        parties.forEach { party ->
            val partyId = requireNotNull(party.id)
            every { partyMemberRepository.findAllByPartyId(partyId) } returns
                listOf(membership(id = partyId, partyId = partyId, joinedAt = LocalDateTime.of(2026, 1, 1, 0, 0)))
        }
    }
}

private const val ME = 1L
private val me = ServiceUser(id = ME, nickname = "나", profileImage = 1)

private fun membership(
    id: Long,
    partyId: Long,
    joinedAt: LocalDateTime,
): PartyMember = PartyMember(id = id, partyId = partyId, serviceUserId = ME, createdAt = joinedAt)

private fun party(
    id: Long,
    name: String,
): Party =
    Party(
        id = id,
        partyName = name,
        inviteCode = "abc12$id",
        ownerId = ME,
        auditMetadata = AuditMetadata(createdAt = LocalDateTime.of(2026, 1, 1, 0, 0)),
    )
