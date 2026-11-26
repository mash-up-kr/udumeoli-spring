package udumeoli.tripphoto.kpi.notification

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

@Configuration(proxyBeanMethods = false)
class DiscordNotificationExecutorConfig {
    @Bean(defaultCandidate = false)
    fun discordNotificationExecutor(): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 1
            setQueueCapacity(QUEUE_CAPACITY)
            setThreadNamePrefix("discord-notification-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        }

    private companion object {
        const val QUEUE_CAPACITY = 1_000
    }
}
