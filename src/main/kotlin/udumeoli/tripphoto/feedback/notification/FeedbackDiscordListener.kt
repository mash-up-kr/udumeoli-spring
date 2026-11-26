package udumeoli.tripphoto.feedback.notification

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import udumeoli.tripphoto.feedback.entity.Feedback
import udumeoli.tripphoto.feedback.event.FeedbackSubmitted
import udumeoli.tripphoto.kpi.notification.DiscordEmbed
import udumeoli.tripphoto.kpi.notification.DiscordEmbedField
import udumeoli.tripphoto.kpi.notification.DiscordEmbedFooter
import udumeoli.tripphoto.kpi.notification.DiscordNotifier
import udumeoli.tripphoto.kpi.notification.DiscordProperties

@Component
class FeedbackDiscordListener(
    private val discordProperties: DiscordProperties,
    private val discordNotifier: DiscordNotifier,
) {
    @Async("discordNotificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onFeedbackSubmitted(event: FeedbackSubmitted) {
        discordNotifier.sendEmbedTo(
            discordProperties.feedbackWebhookUrl,
            createEmbed(event.feedback),
        )
    }

    private fun createEmbed(feedback: Feedback): DiscordEmbed {
        val content = feedback.content.take(MAX_CONTENT_LENGTH)
        val description =
            if (feedback.content.length > MAX_CONTENT_LENGTH) {
                "$content\n\n… 내용이 길어 일부만 표시합니다."
            } else {
                content
            }

        return DiscordEmbed(
            title = "새 서비스 피드백",
            description = description,
            color = EMBED_COLOR,
            fields =
                listOf(
                    DiscordEmbedField(
                        name = "별점",
                        value = "${"⭐".repeat(feedback.rating)} (${feedback.rating}/5)",
                        inline = true,
                    ),
                    DiscordEmbedField(
                        name = "사용자 ID",
                        value = "#${feedback.serviceUserId}",
                        inline = true,
                    ),
                    DiscordEmbedField(
                        name = "앱 버전",
                        value = feedback.appVersion.ifBlank { "미제공" },
                        inline = true,
                    ),
                ),
            footer = DiscordEmbedFooter("피드백 #${feedback.id ?: "-"} · ${feedback.createdAt ?: "시간 정보 없음"}"),
        )
    }

    private companion object {
        const val MAX_CONTENT_LENGTH = 4_000
        const val EMBED_COLOR = 0x5865F2
    }
}
