package udumeoli.tripphoto.config.remote

import com.google.firebase.remoteconfig.ValueSource
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class RemoteConfigService(
    private val properties: RemoteConfigProperties,
    private val environment: Environment,
    private val client: RemoteConfigClient,
) {
    private var nextRefreshAt: Instant = Instant.EPOCH

    // @Profile/ConditionalOnProperty로 빈을 제외하면 기본 프로필로 생성한 AOT 이미지에 빈이 누락된다.
    // 운영 여부와 키는 반드시 런타임에서 확인한다. 생성자에서는 파일/네트워크에 접근하지 않는다.
    @Synchronized
    @Suppress("TooGenericExceptionCaught") // 파일/인증/SDK 오류 모두 도메인 동작에는 기본값으로 격리한다.
    fun getString(
        key: String,
        defaultValue: String,
        userId: String,
        signals: Map<String, String> = emptyMap(),
    ): String {
        require(userId.isNotBlank()) { "Stable user ID required for Remote Config evaluation" }
        if (!configured()) return defaultValue
        if (!Instant.now().isBefore(nextRefreshAt)) {
            nextRefreshAt = Instant.now().plus(properties.refreshInterval.coerceAtLeast(MIN_REFRESH_INTERVAL))
            try {
                client.refresh()
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("Remote Config 조회가 중단되었습니다. 마지막 값 또는 기본값을 사용합니다.")
            } catch (exception: Exception) {
                // 예외 메시지/응답에는 인증 정보가 포함될 수 있어 타입만 기록한다.
                log.warn("Remote Config 조회 실패: {}. 마지막 값 또는 기본값을 사용합니다.", exception.javaClass.simpleName)
            }
        }
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

    private fun configured(): Boolean =
        properties.enabled &&
            environment.acceptsProfiles(Profiles.of("dev & !local & !test")) &&
            properties.projectId.isNotBlank() &&
            properties.credentialsPath.isNotBlank()

    companion object {
        private val MIN_REFRESH_INTERVAL = Duration.parse("PT30S")
        private val log = LoggerFactory.getLogger(RemoteConfigService::class.java)
    }
}
