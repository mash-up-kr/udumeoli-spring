package udumeoli.tripphoto.image.thumbnail

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Future

@Configuration(proxyBeanMethods = false)
@EnableAsync
class ThumbnailExecutorConfig {
    @Bean(defaultCandidate = false)
    fun thumbnailTaskExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = WORKER_COUNT
            maxPoolSize = WORKER_COUNT
            setQueueCapacity(QUEUE_CAPACITY)
            setThreadNamePrefix("thumbnail-")
            setRejectedExecutionHandler { task, executor ->
                (task as? Future<*>)?.cancel(false)
                log.warn("썸네일 생성 요청을 대기열에 넣지 못했습니다: queueSize={}", executor.queue.size)
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(ThumbnailExecutorConfig::class.java)
        private const val WORKER_COUNT = 1
        private const val QUEUE_CAPACITY = 10_000
    }
}
