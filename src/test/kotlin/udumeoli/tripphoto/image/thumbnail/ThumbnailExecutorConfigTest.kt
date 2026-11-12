package udumeoli.tripphoto.image.thumbnail

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ThumbnailExecutorConfigTest {
    @Test
    fun `대기열이 가득 차면 새로 제출된 작업만 취소한다`() {
        val executor = ThumbnailExecutorConfig().thumbnailTaskExecutor()
        executor.setQueueCapacity(1)
        executor.initialize()

        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val running =
                executor.submit(
                    Runnable {
                        started.countDown()
                        release.await()
                    },
                )
            assertThat(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()

            val queued = executor.submit(Runnable {})
            val rejected = executor.submit(Runnable {})

            assertThat(rejected.isCancelled).isTrue()
            release.countDown()
            running.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            queued.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdown()
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 5L
    }
}
