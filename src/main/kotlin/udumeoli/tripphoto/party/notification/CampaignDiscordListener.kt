package udumeoli.tripphoto.party.notification

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import udumeoli.tripphoto.kpi.notification.DiscordEmbed
import udumeoli.tripphoto.kpi.notification.DiscordEmbedField
import udumeoli.tripphoto.kpi.notification.DiscordEmbedFooter
import udumeoli.tripphoto.kpi.notification.DiscordNotifier
import udumeoli.tripphoto.kpi.notification.DiscordProperties
import udumeoli.tripphoto.party.event.CampaignFanJoined

@Component
class CampaignDiscordListener(
    private val discordProperties: DiscordProperties,
    private val discordNotifier: DiscordNotifier,
) {
    @Async("discordNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onFanJoined(event: CampaignFanJoined) {
        val webhookUrl = discordProperties.campaignWebhookUrl.ifBlank { discordProperties.webhookUrl }
        discordNotifier.sendEmbedTo(
            webhookUrl,
            DiscordEmbed(
                title = "🎟️ 팬 코스 새 참여자",
                description = event.partyName,
                color = EMBED_COLOR,
                fields =
                    listOf(
                        DiscordEmbedField("팬", "${event.fanNickname} (#${event.fanUserId})", true),
                        DiscordEmbedField("개인 파티", "#${event.partyId}", true),
                        DiscordEmbedField("캠페인 원본", "#${event.templatePartyId}", true),
                    ),
                footer = DiscordEmbedFooter("가입 완료 · ${event.joinedAt}"),
            ),
        )
    }

    private companion object {
        const val EMBED_COLOR = 0xEB459E
    }
}
