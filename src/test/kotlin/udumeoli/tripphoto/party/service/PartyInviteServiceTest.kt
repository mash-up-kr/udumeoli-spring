package udumeoli.tripphoto.party.service

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
import udumeoli.tripphoto.common.entity.AuditMetadata
import udumeoli.tripphoto.common.graphql.GraphQlDomainException
import udumeoli.tripphoto.common.graphql.GraphQlErrorCode
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.service.UserService
import java.time.LocalDateTime

class PartyInviteServiceTest {
    private lateinit var partyRepository: PartyRepository
    private lateinit var partyMemberRepository: PartyMemberRepository
    private lateinit var userService: UserService
    private lateinit var joinPartyRateLimiter: JoinPartyRateLimiter
    private lateinit var partyInviteService: PartyInviteService

    private val now = LocalDateTime.of(2026, 7, 17, 16, 30)

    @BeforeEach
    fun setUp() {
        partyRepository = mockk()
        partyMemberRepository = mockk()
        userService = mockk()
        joinPartyRateLimiter = mockk()

        val partyQueryService = PartyQueryService(partyRepository, partyMemberRepository, userService)
        partyInviteService =
            PartyInviteService(
                partyRepository = partyRepository,
                partyMemberRepository = partyMemberRepository,
                userService = userService,
                joinPartyRateLimiter = joinPartyRateLimiter,
                inviteCodeIssuer = InviteCodeIssuer(),
                partyQueryService = partyQueryService,
            )
    }

    @Test
    fun `초대코드가 유효하고 정원이 남아 있으면 여행팟에 참여한다`() {
        val owner = user(1, "방장")
        val member = user(2, "멤버")
        val party = party()
        val savedMemberSlot = slot<PartyMember>()

        every { userService.currentUser(2L) } returns member
        every { joinPartyRateLimiter.check(2L) } just Runs
        every { partyRepository.findByInviteCodeForUpdate("abc123") } returns party
        every { partyMemberRepository.existsByPartyIdAndServiceUserId(10L, 2L) } returns false
        every { partyMemberRepository.countByPartyId(10L) } returns 1
        every { partyMemberRepository.save(capture(savedMemberSlot)) } answers {
            savedMemberSlot.captured.copy(id = 101)
        }
        stubPartyPayload(
            partyId = 10,
            memberUserIds = listOf(1, 2),
            users = listOf(owner, member),
        )

        val result = partyInviteService.joinParty(currentUserId = 2, inviteCode = "abc123")

        assertThat(result.members.map { it.nickname }).containsExactly("방장", "멤버")
        assertThat(savedMemberSlot.captured.partyId).isEqualTo(10)
        assertThat(savedMemberSlot.captured.serviceUserId).isEqualTo(2)
        verify { joinPartyRateLimiter.check(2L) }
    }

    @Test
    fun `이미 참여 중인 멤버의 초대코드 참여를 거절한다`() {
        val member = user(2, "멤버")

        every { userService.currentUser(2L) } returns member
        every { joinPartyRateLimiter.check(2L) } just Runs
        every { partyRepository.findByInviteCodeForUpdate("abc123") } returns party()
        every { partyMemberRepository.existsByPartyIdAndServiceUserId(10L, 2L) } returns true

        val thrown =
            catchThrowable {
                partyInviteService.joinParty(currentUserId = 2, inviteCode = "abc123")
            }

        assertThat(thrown).isInstanceOf(GraphQlDomainException::class.java)
        assertThat((thrown as GraphQlDomainException).code).isEqualTo(GraphQlErrorCode.ALREADY_JOINED_PARTY)

        verify(exactly = 0) { partyMemberRepository.save(any<PartyMember>()) }
    }

    @Test
    fun `여행팟 정원이 가득 차면 초대코드 참여를 거절한다`() {
        val member = user(7, "멤버")

        every { userService.currentUser(7L) } returns member
        every { joinPartyRateLimiter.check(7L) } just Runs
        every { partyRepository.findByInviteCodeForUpdate("abc123") } returns party()
        every { partyMemberRepository.existsByPartyIdAndServiceUserId(10L, 7L) } returns false
        every { partyMemberRepository.countByPartyId(10L) } returns PartyInviteService.MAX_PARTY_MEMBERS

        val thrown =
            catchThrowable {
                partyInviteService.joinParty(currentUserId = 7, inviteCode = "abc123")
            }

        assertThat(thrown).isInstanceOf(GraphQlDomainException::class.java)
        assertThat((thrown as GraphQlDomainException).code).isEqualTo(GraphQlErrorCode.PARTY_FULL)

        verify(exactly = 0) { partyMemberRepository.save(any<PartyMember>()) }
    }

    private fun stubPartyPayload(
        partyId: Long,
        memberUserIds: List<Long>,
        users: List<ServiceUser>,
    ) {
        val usersById = users.associateBy { requireNotNull(it.id) }

        every { partyMemberRepository.findAllByPartyId(partyId) } returns
            memberUserIds.mapIndexed { index, userId ->
                PartyMember(id = index + 1L, partyId = partyId, serviceUserId = userId)
            }
        every { userService.findAllById(any<Iterable<Long>>()) } answers {
            firstArg<Iterable<Long>>().mapNotNull { usersById[it] }
        }
    }

    private fun user(
        id: Long,
        nickname: String,
    ): ServiceUser = ServiceUser(id = id, nickname = nickname)

    private fun party(): Party =
        Party(
            id = 10,
            partyName = "우리 팟",
            inviteCode = "abc123",
            ownerId = 1,
            auditMetadata = audit(),
        )

    private fun audit(): AuditMetadata = AuditMetadata(createdAt = now, updatedAt = now)
}
