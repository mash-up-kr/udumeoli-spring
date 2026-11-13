package udumeoli.tripphoto.config.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant

class DiscordWebhookAppender : AppenderBase<ILoggingEvent>() {
    private val objectMapper = ObjectMapper()
    private var endpoint: URL? = null

    override fun start() {
        val webhookUrl = System.getenv(WEBHOOK_URL_ENV)?.trim().orEmpty()
        endpoint = parseEndpoint(webhookUrl)
        if (endpoint == null && webhookUrl.isNotEmpty()) {
            addWarn("${WEBHOOK_URL_ENV}에 유효한 HTTPS 주소가 필요합니다.")
        }
        super.start()
    }

    override fun append(event: ILoggingEvent) {
        val webhook = endpoint ?: return
        val applicationName = context.getProperty("APP_NAME") ?: "TripPhoto"
        val message =
            buildString {
                appendLine("[$applicationName] [${event.level}] ${Instant.ofEpochMilli(event.timeStamp)}")
                appendLine(event.loggerName)
                append(event.formattedMessage)
                event.throwableProxy?.let {
                    appendLine()
                    append(ThrowableProxyUtil.asString(it))
                }
            }
        val content =
            if (message.length > CONTENT_LIMIT) {
                message.take(CONTENT_LIMIT - 1).trimEnd { it.isHighSurrogate() } + "…"
            } else {
                message
            }
        val payload = objectMapper.createObjectNode().put("content", content)
        payload.putObject("allowed_mentions").putArray("parse")

        try {
            send(webhook, objectMapper.writeValueAsBytes(payload))
        } catch (e: IOException) {
            addWarn("Discord 로그 전송 실패 (${e.javaClass.simpleName})")
        }
    }

    private fun send(
        webhook: URL,
        body: ByteArray,
    ) {
        val connection = webhook.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = HTTP_TIMEOUT_MILLIS
            connection.readTimeout = HTTP_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            if (HttpStatus.Series.resolve(status) != HttpStatus.Series.SUCCESSFUL) {
                addWarn("Discord 로그 전송 실패 (HTTP $status)")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEndpoint(value: String): URL? =
        try {
            URI
                .create(value)
                .takeIf { it.scheme.equals("https", ignoreCase = true) && it.host != null && it.userInfo == null }
                ?.toURL()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IOException) {
            null
        }

    companion object {
        private const val WEBHOOK_URL_ENV = "DISCORD_LOG_WEBHOOK_URL"
        private const val CONTENT_LIMIT = 2_000
        private const val HTTP_TIMEOUT_MILLIS = 3_000
    }
}
