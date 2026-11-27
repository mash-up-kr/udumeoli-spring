package udumeoli.tripphoto.kpi.notification

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("discord")
data class DiscordProperties(
    val webhookUrl: String = "",
    val feedbackWebhookUrl: String = "",
    val campaignWebhookUrl: String = "",
)
