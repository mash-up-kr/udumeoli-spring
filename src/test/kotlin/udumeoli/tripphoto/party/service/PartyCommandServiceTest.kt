package udumeoli.tripphoto.party.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import udumeoli.tripphoto.common.entity.AuditMetadata
import udumeoli.tripphoto.party.entity.Party
import udumeoli.tripphoto.party.entity.PartyMember
import udumeoli.tripphoto.party.repository.PartyMemberRepository
import udumeoli.tripphoto.party.repository.PartyRepository
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.service.UserService
import java.time.LocalDateTime

class PartyCommandServiceTest {
    private lateinit var partyRepository: PartyRepository
    private lateinit var partyMemberRepository: PartyMemberRepository
    private lateinit var userService: UserService
    private lateinit var partyCommandService: PartyCommandService

    private val now = LocalDateTime.of(2026, 7, 17, 16, 30)

    @BeforeEach
    fun setUp() {
        partyRepository = mockk()
        partyMemberRepository = mockk()
        userService = mockk()

        val partyQueryService = PartyQueryService(partyRepository, partyMemberRepository, userService)
        partyCommandService =
            PartyCommandService(
                partyRepository = partyRepository,
                partyMemberRepository = partyMemberRepository,
                userService = userService,
                inviteCodeIssuer = InviteCodeIssuer(),
                partyQueryService = partyQueryService,
            )
    }

    @Test
    fun `여행팟 생성 시 초대코드와 방장 멤버십을 만든다`() {
        val owner = user(1, "방장")
        val savedPartySlot = slot<Party>()
        val savedMemberSlot = slot<PartyMember>()

        every { userService.currentUser(1L) } returns owner
        every { partyRepository.save(capture(savedPartySlot)) } answers {
            savedPartySlot.captured.copy(id = 10, auditMetadata = audit())
        }
        every { partyMemberRepository.save(capture(savedMemberSlot)) } answers {
            savedMemberSlot.captured.copy(id = 100)
        }
        stubPartyPayload(
            partyId = 10,
            memberUserIds = listOf(1),
            users = listOf(owner),
        )

        val result = partyCommandService.createParty(currentUserId = 1, name = "유지정민")

        assertThat(result.name).isEqualTo("유지정민")
        assertThat(result.owner.nickname).isEqualTo("방장")
        assertThat(result.members.map { it.nickname }).containsExactly("방장")
        assertThat(savedPartySlot.captured.inviteCode).hasSize(6)
        assertThat(savedPartySlot.captured.inviteCode).matches("[0-9a-z]+")
        assertThat(savedMemberSlot.captured.partyId).isEqualTo(10)
        assertThat(savedMemberSlot.captured.serviceUserId).isEqualTo(1)
    }

    @Test
    fun `초대코드가 유니크 인덱스와 충돌하면 재시도한다`() {
        val owner = user(1, "방장")
        var saveAttempts = 0

        every { userService.currentUser(1L) } returns owner
        every { partyRepository.save(any<Party>()) } answers {
            saveAttempts += 1
            if (saveAttempts == 1) {
                throw DuplicateKeyException("duplicate invite code")
            }
            firstArg<Party>().copy(id = 10, auditMetadata = audit())
        }
        every { partyMemberRepository.save(any<PartyMember>()) } answers {
            firstArg<PartyMember>().copy(id = 100)
        }
        stubPartyPayload(
            partyId = 10,
            memberUserIds = listOf(1),
            users = listOf(owner),
        )

        val result = partyCommandService.createParty(currentUserId = 1, name = "유지정민")

        assertThat(result.name).isEqualTo("유지정민")
        assertThat(saveAttempts).isEqualTo(2)
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

    private fun audit(): AuditMetadata = AuditMetadata(createdAt = now, updatedAt = now)
}
