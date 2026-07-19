package udumeoli.tripphoto.image.thumbnail

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * 썸네일 생성 서버(Go, POST /thumbnail) 연동 — 생성 요청을 접수시킨다.
 *
 * 실제 서버의 동작 방식에 맞춘 계약이다:
 * - 요청은 접수만 되고 백그라운드 워커가 순차 처리한다 — fire-and-forget.
 *   202 = 큐 접수됨, 503 = 큐(버퍼 100) 가득참. 어느 쪽이든 결과는 돌아오지 않는다.
 * - 완료 콜백이 없다. 서버가 image.thumbnail_url을 **DB에 직접 UPDATE**한다.
 *   따라서 백엔드에는 완료를 수신하는 코드가 존재하지 않는다.
 * - 어느 단계든 실패하면 재시도 없이 thumbnail_url이 null로 남고, 프론트가 원본으로 폴백한다.
 * - imageUrl은 서버가 인증 없이 HTTP GET으로 내려받는다 → 원본이 공개 접근 가능해야 한다 (D2 전제).
 * - server-url 미설정(빈 값)이면 요청을 건너뛴다: 로컬 개발·테스트에서 썸네일 서버 없이 동작하기 위함.
 * - 4xx/5xx는 예외로 던진다 — 삼킬지 말지는 호출자(ImageService.requestThumbnails)가 결정한다.
 *
 * @RegisterReflectionForBinding: ThumbnailRequest는 RestClient.body()로만 쓰여 컨트롤러 타입이
 * 아니므로 Spring AOT가 직렬화 리플렉션 힌트를 등록하지 않는다 — 힌트 없이는 Native Image에서
 * Jackson 직렬화가 런타임 실패한다 (FlywayNativeHints와 같은 계열의 Native 호환성 조치).
 */
@RegisterReflectionForBinding(ThumbnailRequest::class)
@Component
class HttpThumbnailAdapter(
    private val properties: ThumbnailProperties,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder.build()

    fun requestThumbnail(
        imageId: Long,
        imageUrl: String,
    ) {
        if (properties.serverUrl.isBlank()) {
            log.info("썸네일 서버 미설정 — 생성 요청 건너뜀: imageId={}", imageId)
            return
        }
        restClient
            .post()
            .uri("${properties.serverUrl.trimEnd('/')}/thumbnail")
            .contentType(MediaType.APPLICATION_JSON)
            .body(ThumbnailRequest(id = imageId, imageUrl = imageUrl))
            .retrieve()
            .toBodilessEntity()
    }

    companion object {
        private val log = LoggerFactory.getLogger(HttpThumbnailAdapter::class.java)
    }
}

/** 썸네일 서버의 요청 형식과 1:1 — { "id": 123, "image_url": "https://..." } */
data class ThumbnailRequest(
    val id: Long,
    @JsonProperty("image_url")
    val imageUrl: String,
)
