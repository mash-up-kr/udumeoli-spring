package udumeoli.tripphoto.image.thumbnail

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient

/** 썸네일 서버(Go)의 실제 요청 형식(POST /thumbnail, {id, image_url})과 맞는지 검증한다. */
class HttpThumbnailAdapterTest {
    @Test
    fun `POST thumbnail로 id와 image_url을 보낸다`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = HttpThumbnailAdapter(ThumbnailProperties(serverUrl = "http://thumb.example.com"), builder)
        server
            .expect(requestTo("http://thumb.example.com/thumbnail"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.image_url").value("https://cdn.example.com/original/1.jpg"))
            .andRespond(withStatus(HttpStatus.ACCEPTED))

        adapter.requestThumbnail(1L, "https://cdn.example.com/original/1.jpg")

        server.verify()
    }

    @Test
    fun `server-url이 비어 있으면 요청을 보내지 않는다 (로컬 개발 모드)`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val adapter = HttpThumbnailAdapter(ThumbnailProperties(serverUrl = ""), builder)

        adapter.requestThumbnail(1L, "https://cdn.example.com/original/1.jpg")

        server.verify()
    }
}
