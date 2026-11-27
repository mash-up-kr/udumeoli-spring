package udumeoli.tripphoto.kpi.notification

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.party.event.PartyCopied

class DiscordNotificationListenerTest {
    @Test
    fun `party copy sends an embed to the configured webhook`() {
        val notifier = mockk<DiscordNotifier>()
        val embedSlot = slot<DiscordEmbed>()
        every { notifier.sendEmbedTo("https://example.com/copy-webhook", capture(embedSlot)) } just Runs
        val listener =
            DiscordNotificationListener(
                DiscordProperties(
                    webhookUrl = "https://example.com/kpi-webhook",
                    partyCopyWebhookUrl = "https://example.com/copy-webhook",
                ),
                notifier,
            )

        listener.onPartyCopied(
            PartyCopied(
                sourcePartyId = 10,
                copiedPartyId = 20,
                partyName = "원희 팬 코스",
                copiedByUserId = 30,
                copiedByNickname = "팬",
            ),
        )

        verify(exactly = 1) { notifier.sendEmbedTo("https://example.com/copy-webhook", any()) }
        assertThat(embedSlot.captured.title).contains("팟 복사")
        assertThat(embedSlot.captured.fields.map { it.value }).contains("팬 (#30)", "#20", "#10")
    }
}
