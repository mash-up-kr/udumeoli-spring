package udumeoli.tripphoto.kpi.notification

import org.slf4j.LoggerFactory
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@RegisterReflectionForBinding(
    DiscordMessage::class,
    DiscordEmbedMessage::class,
    DiscordEmbed::class,
    DiscordEmbedField::class,
    DiscordEmbedFooter::class,
    DiscordAllowedMentions::class,
)
@Component
class DiscordNotifier(
    private val properties: DiscordProperties,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder.build()

    fun send(message: String) = sendTo(properties.webhookUrl, DiscordMessage(content = message))

    fun sendTo(
        webhookUrl: String,
        message: String,
    ) = sendTo(webhookUrl, DiscordMessage(content = message))

    fun sendEmbedTo(
        webhookUrl: String,
        embed: DiscordEmbed,
    ) = sendTo(
        webhookUrl,
        DiscordEmbedMessage(
            embeds = listOf(embed),
            allowedMentions = DiscordAllowedMentions(parse = emptyList()),
        ),
    )

    private fun sendTo(
        webhookUrl: String,
        body: Any,
    ) {
        if (webhookUrl.isBlank()) {
            log.info("디스코드 웹훅 미설정 — 알림 전송 건너뜀")
            return
        }
        try {
            restClient
                .post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity()
        } catch (exception: Exception) {
            // Webhook URLs are credentials, so avoid logging exception messages that may contain the URL.
            log.warn("디스코드 웹훅 전송 실패: {}", exception.javaClass.simpleName)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DiscordNotifier::class.java)
    }
}

data class DiscordMessage(
    val content: String,
)

data class DiscordEmbedMessage(
    val embeds: List<DiscordEmbed>,
    @com.fasterxml.jackson.annotation.JsonProperty("allowed_mentions")
    val allowedMentions: DiscordAllowedMentions,
)

data class DiscordEmbed(
    val title: String,
    val description: String,
    val color: Int,
    val fields: List<DiscordEmbedField>,
    val footer: DiscordEmbedFooter,
)

data class DiscordEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean,
)

data class DiscordEmbedFooter(
    val text: String,
)

data class DiscordAllowedMentions(
    val parse: List<String>,
)
