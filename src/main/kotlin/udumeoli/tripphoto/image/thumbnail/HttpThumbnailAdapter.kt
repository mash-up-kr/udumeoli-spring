package udumeoli.tripphoto.image.thumbnail

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

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

data class ThumbnailRequest(
    val id: Long,
    @JsonProperty("image_url")
    val imageUrl: String,
)
