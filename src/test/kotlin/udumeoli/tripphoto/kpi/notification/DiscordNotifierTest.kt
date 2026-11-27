package udumeoli.tripphoto.kpi.notification

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.web.client.RestClient

class DiscordNotifierTest {
    @Test
    fun `sendEmbedTo serializes allowed_mentions parse as empty array`() {
        val builder =
            RestClient.builder().messageConverters { converters ->
                converters.removeIf { it is MappingJackson2HttpMessageConverter }
                converters.add(MappingJackson2HttpMessageConverter(jacksonObjectMapper()))
            }
        val server = MockRestServiceServer.bindTo(builder).build()
        server
            .expect(requestTo("https://example.com/webhook"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.embeds[0].title").value("테스트"))
            .andExpect(jsonPath("$.allowed_mentions.parse").isArray)
            .andExpect(jsonPath("$.allowed_mentions.parse").isEmpty)
            .andRespond(withNoContent())

        val notifier = DiscordNotifier(DiscordProperties(), builder)
        notifier.sendEmbedTo(
            "https://example.com/webhook",
            DiscordEmbed(
                title = "테스트",
                description = "설명",
                color = 0x5865F2,
                fields = listOf(DiscordEmbedField("항목", "값", true)),
                footer = DiscordEmbedFooter("푸터"),
            ),
        )

        server.verify()
    }
}
