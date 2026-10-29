package udumeoli.tripphoto.config.remote

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.io.IOException

class RemoteConfigServiceTest {
    private val client = mockk<RemoteConfigClient>(relaxed = true)
    private val configured = RemoteConfigProperties(true, "project", "/not-read-in-tests.json")

    @Test
    fun `운영 외 프로필은 enabled와 키가 설정되어도 외부 접근하지 않는다`() {
        listOf(emptyArray(), arrayOf("local"), arrayOf("test"), arrayOf("dev", "local")).forEach { profiles ->
            val environment = MockEnvironment().apply { setActiveProfiles(*profiles) }
            val service = RemoteConfigService(configured, environment, client)
            assertThat(service.getString("variant", "control", "user-1")).isEqualTo("control")
        }
        verify(exactly = 0) { client.refresh() }
        verify(exactly = 0) { client.evaluate(any(), any()) }
    }

    @Test
    fun `운영도 키 미설정이나 비활성화 상태에서는 외부 접근하지 않는다`() {
        listOf(configured.copy(enabled = false), configured.copy(credentialsPath = ""), configured.copy(projectId = ""))
            .forEach { properties ->
                assertThat(service(properties).getString("variant", "control", "user-1")).isEqualTo("control")
            }
        verify(exactly = 0) { client.refresh() }
    }

    @Test
    fun `조회 장애에는 기본값을 반환하고 요청마다 재시도하지 않는다`() {
        every { client.refresh() } throws IOException("private error")
        every { client.evaluate(any(), any()) } returns null
        val service = service(configured)
        repeat(3) { assertThat(service.getString("variant", "control", "user-1")).isEqualTo("control") }
        verify(exactly = 1) { client.refresh() }
    }

    private fun service(properties: RemoteConfigProperties): RemoteConfigService =
        RemoteConfigService(properties, MockEnvironment().apply { setActiveProfiles("dev") }, client)
}
