package udumeoli.tripphoto.config.remote

import com.google.api.core.ApiFutures
import com.google.firebase.remoteconfig.ServerConfig
import com.google.firebase.remoteconfig.ServerTemplate
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RemoteConfigClientTest {
    @Test
    fun `갱신이 네트워크 응답을 기다리는 동안에도 요청은 캐시를 평가한다`() {
        val client = RemoteConfigClient(RemoteConfigProperties())
        val template = mockk<ServerTemplate>()
        val cached = mockk<ServerConfig>()
        val fetching = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        ReflectionTestUtils.setField(client, "template", template)
        every { template.evaluate(any()) } returns cached
        every { template.load() } answers {
            fetching.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            ApiFutures.immediateFuture(null)
        }
        try {
            val refresh = executor.submit { client.refresh() }
            assertThat(fetching.await(5, TimeUnit.SECONDS)).isTrue()
            assertTimeoutPreemptively(Duration.ofSeconds(1)) {
                assertThat(client.evaluate("user-1", emptyMap())).isSameAs(cached)
            }
            release.countDown()
            refresh.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
            client.close()
        }
    }
}
