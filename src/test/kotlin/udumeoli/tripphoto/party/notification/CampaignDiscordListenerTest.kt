package udumeoli.tripphoto.party.notification

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.kpi.notification.DiscordEmbed
import udumeoli.tripphoto.kpi.notification.DiscordNotifier
import udumeoli.tripphoto.kpi.notification.DiscordProperties
import udumeoli.tripphoto.party.event.CampaignFanJoined

class CampaignDiscordListenerTest {
    @Test
    fun `fan join alert uses campaign webhook and identifies fan and party`() {
        val notifier = mockk<DiscordNotifier>()
        val embed = slot<DiscordEmbed>()
        every { notifier.sendEmbedTo("https://example.com/campaign", capture(embed)) } just Runs
        val listener =
            CampaignDiscordListener(
                DiscordProperties(
                    webhookUrl = "https://example.com/kpi",
                    campaignWebhookUrl = "https://example.com/campaign",
                ),
                notifier,
            )

        listener.onFanJoined(
            CampaignFanJoined(
                templatePartyId = 10,
                partyId = 20,
                partyName = "원희 따라 걷는 창원 · 팬 코스",
                fanUserId = 30,
                fanNickname = "팬하나",
            ),
        )

        verify(exactly = 1) { notifier.sendEmbedTo("https://example.com/campaign", any()) }
        assertThat(embed.captured.title).contains("팬 코스")
        assertThat(embed.captured.description).contains("원희")
        assertThat(embed.captured.fields.map { it.value }).contains("팬하나 (#30)", "#20", "#10")
    }

    @Test
    fun `existing KPI webhook receives campaign alerts when no campaign webhook is configured`() {
        val notifier = mockk<DiscordNotifier>()
        every { notifier.sendEmbedTo("https://example.com/kpi", any()) } just Runs
        val listener = CampaignDiscordListener(DiscordProperties(webhookUrl = "https://example.com/kpi"), notifier)

        listener.onFanJoined(CampaignFanJoined(10, 20, "팬 코스", 30, "팬하나"))

        verify(exactly = 1) { notifier.sendEmbedTo("https://example.com/kpi", any()) }
    }
}
