package udumeoli.tripphoto.image.thumbnail

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

@RegisterReflectionForBinding(ThumbnailRequest::class)
@Component
class HttpThumbnailAdapter(
    private val properties: ThumbnailProperties,
    restClientBuilder: RestClient.Builder,
    requestFactorySettings: ClientHttpRequestFactorySettings,
) {
    private val restClient =
        restClientBuilder
            .requestFactory(ClientHttpRequestFactoryBuilder.simple().build(requestFactorySettings))
            .build()

    @Async("thumbnailTaskExecutor")
    fun requestThumbnail(
        imageId: Long,
        imageUrl: String,
    ) {
        if (properties.serverUrl.isBlank()) {
            return
        }
        try {
            restClient
                .post()
                .uri("${properties.serverUrl.trimEnd('/')}/thumbnail")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ThumbnailRequest(id = imageId, imageUrl = imageUrl))
                .retrieve()
                .toBodilessEntity()
        } catch (e: RestClientResponseException) {
            log.warn("썸네일 생성 요청 실패: imageId={}, status={}", imageId, e.statusCode.value())
        } catch (e: RestClientException) {
            log.warn("썸네일 생성 요청 실패: imageId={}, cause={}", imageId, e.mostSpecificCause.javaClass.simpleName)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(HttpThumbnailAdapter::class.java)
    }
}

data class ThumbnailRequest(
    val id: Long,
    @JsonProperty("image_url")
    val imageUrl: String,
)
