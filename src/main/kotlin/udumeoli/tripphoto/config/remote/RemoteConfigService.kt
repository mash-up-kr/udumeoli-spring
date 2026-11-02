package udumeoli.tripphoto.config.remote

import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.ValueSource
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.NestedExceptionUtils
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ScheduledFuture

@Service
class RemoteConfigService(
    private val properties: RemoteConfigProperties,
    private val environment: Environment,
    private val client: RemoteConfigClient,
    @Qualifier("remoteConfigScheduler") private val scheduler: TaskScheduler,
) {
    private var refreshTask: ScheduledFuture<*>? = null

    // @Profile/ConditionalOnProperty로 빈을 제외하면 기본 프로필로 생성한 AOT 이미지에 빈이 누락된다.
    // 실제 앱 시작 이벤트에서 운영 여부와 키를 확인하고, 파일/네트워크 접근은 백그라운드에서 수행한다.
    @EventListener(ApplicationReadyEvent::class)
    @Synchronized
    fun start() {
        if (refreshTask != null) return
        val reason = disabledReason()
        if (reason != null) {
            log.info("Firebase Remote Config 비활성화: {}", reason)
            return
        }
        val interval = properties.refreshInterval.coerceAtLeast(MIN_REFRESH_INTERVAL)
        log.info(
            "Firebase Remote Config 활성화: projectId={}, 갱신 간격={}. 백그라운드 최초 조회를 시작합니다.",
            properties.projectId,
            interval,
        )
        refreshTask = scheduler.scheduleWithFixedDelay(::refresh, interval)
    }

    @Suppress("TooGenericExceptionCaught") // 파일/인증/SDK 오류가 있어도 주기적 갱신을 계속한다.
    private fun refresh() {
        try {
            client.refresh()
            log.info("Firebase Remote Config 조회 성공: projectId={}", properties.projectId)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn("Firebase Remote Config 조회가 중단되었습니다. 마지막 값 또는 기본값을 사용합니다.")
        } catch (exception: FirebaseRemoteConfigException) {
            // 응답 본문/메시지 대신 오류 코드와 원인 타입만 남긴다.
            log.warn(
                "Firebase Remote Config 조회 실패: {}. errorCode={}, remoteConfigErrorCode={}, httpStatus={}, cause={}. " +
                    "마지막 값 또는 기본값을 사용합니다.",
                exception.javaClass.simpleName,
                exception.errorCode,
                exception.remoteConfigErrorCode,
                exception.httpResponse?.statusCode,
                NestedExceptionUtils.getMostSpecificCause(exception).javaClass.simpleName,
            )
        } catch (exception: Exception) {
            // 예외 메시지/응답에는 인증 정보가 포함될 수 있어 타입만 기록한다.
            log.warn("Firebase Remote Config 조회 실패: {}. 마지막 값 또는 기본값을 사용합니다.", exception.javaClass.simpleName)
        }
    }

    @Suppress("TooGenericExceptionCaught") // SDK 오류는 기본값으로 격리한다.
    fun getString(
        key: String,
        defaultValue: String,
        userId: String,
        signals: Map<String, String> = emptyMap(),
    ): String {
        require(userId.isNotBlank()) { "Stable user ID required for Remote Config evaluation" }
        if (disabledReason() != null) return defaultValue
        return try {
            val config = client.evaluate(userId, signals)
            if (config == null || config.getValueSource(key) == ValueSource.STATIC) {
                defaultValue
            } else {
                config.getString(key)
            }
        } catch (exception: Exception) {
            log.warn("Remote Config 평가 실패: {}. 기본값을 사용합니다.", exception.javaClass.simpleName)
            defaultValue
        }
    }

    fun getBoolean(
        key: String,
        defaultValue: Boolean,
        userId: String,
        signals: Map<String, String> = emptyMap(),
    ): Boolean = getString(key, "", userId, signals).toBooleanStrictOrNull() ?: defaultValue

    fun getLong(
        key: String,
        defaultValue: Long,
        userId: String,
        signals: Map<String, String> = emptyMap(),
    ): Long = getString(key, "", userId, signals).toLongOrNull() ?: defaultValue

    private fun disabledReason(): String? =
        when {
            !environment.acceptsProfiles(Profiles.of("dev & !local & !test")) -> "운영 프로필이 아닙니다."
            !properties.enabled -> "FIREBASE_REMOTE_CONFIG_ENABLED=false"
            properties.projectId.isBlank() -> "FIREBASE_PROJECT_ID 미설정"
            properties.credentialsPath.isBlank() -> "FIREBASE_CREDENTIALS_PATH 미설정"
            else -> null
        }

    @PreDestroy
    @Synchronized
    fun close() {
        refreshTask?.cancel(true)
        refreshTask = null
    }

    companion object {
        private val MIN_REFRESH_INTERVAL = Duration.parse("PT30S")
        private val log = LoggerFactory.getLogger(RemoteConfigService::class.java)
    }
}
