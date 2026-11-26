package udumeoli.tripphoto.feedback.notification

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

@Configuration(proxyBeanMethods = false)
class FeedbackDiscordExecutorConfig {
    @Bean(defaultCandidate = false)
    fun feedbackDiscordExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 1
            setQueueCapacity(QUEUE_CAPACITY)
            setThreadNamePrefix("feedback-discord-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        }

    private companion object {
        const val QUEUE_CAPACITY = 1_000
    }
}
