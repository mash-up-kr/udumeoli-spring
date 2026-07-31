package udumeoli.tripphoto.party.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PartyTest {
    @Test
    fun `방장 여부를 판단한다`() {
        val party = party()

        assertThat(party.isOwner(1)).isTrue()
        assertThat(party.isOwner(2)).isFalse()
    }

    @Test
    fun `방장은 나갈 수 없고 멤버는 나갈 수 있다`() {
        val party = party()

        assertThat(party.canLeave(1)).isFalse()
        assertThat(party.canLeave(2)).isTrue()
    }

    @Test
    fun `방장은 강퇴할 수 없고 멤버는 강퇴할 수 있다`() {
        val party = party()

        assertThat(party.canKick(1)).isFalse()
        assertThat(party.canKick(2)).isTrue()
    }

    private fun party(): Party =
        Party(
            id = 10,
            partyName = "우리 팟",
            inviteCode = "abc123",
            ownerId = 1,
        )
}
