package udumeoli.tripphoto.config.remote

import com.google.firebase.ErrorCode
import com.google.firebase.IncomingHttpResponse
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.ServerConfig
import com.google.firebase.remoteconfig.ValueSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.mock.env.MockEnvironment
import org.springframework.scheduling.TaskScheduler
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ScheduledFuture

@ExtendWith(OutputCaptureExtension::class)
class RemoteConfigServiceTest {
    private val client = mockk<RemoteConfigClient>(relaxed = true)
    private val configured = RemoteConfigProperties(true, "project", "/not-read-in-tests.json")
    private val task = slot<Runnable>()
    private val scheduled = mockk<ScheduledFuture<*>>(relaxed = true)
    private val scheduler =
        mockk<TaskScheduler> {
            every { scheduleWithFixedDelay(capture(task), any<Duration>()) } returns scheduled
        }

    @Test
    fun `운영 외 프로필은 enabled와 키가 설정되어도 외부 접근하지 않는다`() {
        listOf(emptyArray(), arrayOf("local"), arrayOf("test"), arrayOf("dev", "local")).forEach { profiles ->
            val environment = MockEnvironment().apply { setActiveProfiles(*profiles) }
            val service = RemoteConfigService(configured, environment, client, scheduler)
            service.start()
            assertThat(service.getString("variant", "control", "user-1")).isEqualTo("control")
        }
        verify(exactly = 0) { scheduler.scheduleWithFixedDelay(any<Runnable>(), any<Duration>()) }
        verify(exactly = 0) { client.refresh() }
        verify(exactly = 0) { client.evaluate(any(), any()) }
    }

    @Test
    fun `운영도 키 미설정이나 비활성화 상태에서는 외부 접근하지 않는다`() {
        listOf(configured.copy(enabled = false), configured.copy(credentialsPath = ""), configured.copy(projectId = ""))
            .forEach { properties ->
                val service = service(properties)
                service.start()
                assertThat(service.getString("variant", "control", "user-1")).isEqualTo("control")
            }
        verify(exactly = 0) { scheduler.scheduleWithFixedDelay(any<Runnable>(), any<Duration>()) }
        verify(exactly = 0) { client.refresh() }
    }

    @Test
    fun `시작 이벤트는 조회를 기다리지 않고 최초 조회와 주기 갱신을 예약한다`(output: CapturedOutput) {
        val service = service(configured)
        service.start()
        service.start()
        verify(exactly = 1) { scheduler.scheduleWithFixedDelay(any<Runnable>(), Duration.ofMinutes(5)) }
        verify(exactly = 0) { client.refresh() }

        task.captured.run()
        verify(exactly = 1) { client.refresh() }
        assertThat(output.all).contains("Firebase Remote Config 활성화", "Firebase Remote Config 조회 성공")
        assertThat(output.all).doesNotContain(configured.credentialsPath)
    }

    @Test
    fun `요청은 시작 전에도 네트워크 조회 없이 기본값을 사용한다`() {
        every { client.evaluate(any(), any()) } returns null
        val service = service(configured)
        repeat(3) { assertThat(service.getString("variant", "control", "user-1")).isEqualTo("control") }
        verify(exactly = 0) { client.refresh() }
    }

    @Test
    fun `조회 실패 후에도 다음 주기에 재시도하고 마지막 값을 유지한다`(output: CapturedOutput) {
        val config = mockk<ServerConfig>()
        every { config.getValueSource("variant") } returns ValueSource.REMOTE
        every { config.getString("variant") } returns "treatment"
        var cached: ServerConfig? = null
        var attempts = 0
        every { client.evaluate(any(), any()) } answers { cached }
        every { client.refresh() } answers {
            attempts++
            if (attempts != 2) throw IOException("private error")
            cached = config
        }
        val service = service(configured)
        service.start()

        task.captured.run()
        assertThat(service.getString("variant", "control", "user-1")).isEqualTo("control")
        task.captured.run()
        assertThat(service.getString("variant", "control", "user-1")).isEqualTo("treatment")
        task.captured.run()
        assertThat(service.getString("variant", "control", "user-1")).isEqualTo("treatment")
        verify(exactly = 3) { client.refresh() }
        assertThat(output.all).contains("Firebase Remote Config 조회 실패: IOException")
        assertThat(output.all).doesNotContain("private error")
    }

    @Test
    fun `Firebase 조회 실패 로그는 오류 코드와 HTTP 상태만 기록한다`(output: CapturedOutput) {
        val response = mockk<IncomingHttpResponse>()
        every { response.statusCode } returns 404
        every { client.evaluate(any(), any()) } returns null
        every { client.refresh() } throws
            FirebaseRemoteConfigException(
                ErrorCode.NOT_FOUND,
                "private response body",
                IOException("private cause message"),
                response,
                null,
            )
        val service = service(configured)
        service.start()
        task.captured.run()

        assertThat(service.getString("variant", "control", "user-1")).isEqualTo("control")
        assertThat(output.all).contains("errorCode=NOT_FOUND", "httpStatus=404", "cause=IOException")
        assertThat(output.all).doesNotContain("private response body", "private cause message")
        verify(exactly = 0) { response.content }
        verify(exactly = 0) { response.headers }
    }

    @Test
    fun `지나치게 짧은 갱신 간격은 최소 간격을 사용하고 종료 시 예약을 취소한다`() {
        val service = service(configured.copy(refreshInterval = Duration.ofSeconds(1)))
        service.start()
        verify(exactly = 1) { scheduler.scheduleWithFixedDelay(any<Runnable>(), Duration.ofSeconds(30)) }
        service.close()
        verify(exactly = 1) { scheduled.cancel(true) }
    }

    private fun service(properties: RemoteConfigProperties): RemoteConfigService =
        RemoteConfigService(properties, MockEnvironment().apply { setActiveProfiles("dev") }, client, scheduler)
}
