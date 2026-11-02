package udumeoli.tripphoto.config.remote

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration(proxyBeanMethods = false)
class RemoteConfigSchedulingConfig {
    @Bean
    fun remoteConfigScheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("remote-config-")
            setRemoveOnCancelPolicy(true)
            setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS)
        }

    companion object {
        private const val SHUTDOWN_WAIT_SECONDS = 10
    }
}
