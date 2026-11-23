package udumeoli.tripphoto.kpi.notification

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class DiscordNotifier(
    private val properties: DiscordProperties,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder.build()

    fun send(message: String) {
        if (properties.webhookUrl.isBlank()) {
            log.info("디스코드 웹훅 미설정 — 알림 전송 건너뜀")
            return
        }
        restClient
            .post()
            .uri(properties.webhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .body(DiscordMessage(content = message))
            .retrieve()
            .toBodilessEntity()
    }

    companion object {
        private val log = LoggerFactory.getLogger(DiscordNotifier::class.java)
    }
}

data class DiscordMessage(
    val content: String,
)
