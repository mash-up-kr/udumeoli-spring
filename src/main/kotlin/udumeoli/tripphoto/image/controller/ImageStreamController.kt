package udumeoli.tripphoto.image.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import udumeoli.tripphoto.image.service.ImageService
import java.net.URI
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Suppress("MagicNumber", "UnusedPrivateProperty", "UnusedParameter")
@RestController
class ImageStreamController(
    private val imageService: ImageService,
    @Value("\${app.security.kms-master-key}") private val masterKey: String,
) {
    /**
     * 클라이언트(브라우저)에서 <img src="/api/images/{objectKey}"> 로 호출할 때
     * 서버는 권한을 체크한 뒤, Go 썸네일 서버(Nginx 경유)로 302 Redirect 시킵니다.
     */
    @GetMapping("/api/images/{objectKey}")
    fun redirectOriginal(
        @PathVariable objectKey: String,
    ): ResponseEntity<Void> = createRedirect(objectKey, "original")

    @GetMapping("/api/images/{objectKey}/thumbnail")
    fun redirectThumbnail(
        @PathVariable objectKey: String,
    ): ResponseEntity<Void> = createRedirect(objectKey, "thumb")

    private fun createRedirect(
        objectKey: String,
        type: String,
    ): ResponseEntity<Void> {
        val expires = (System.currentTimeMillis() / 1000) + 3600
        val payload = "$objectKey:$expires"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sigBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        val sig = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes)

        val targetUrl = "/stream/$objectKey?type=$type&expires=$expires&sig=$sig"

        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(targetUrl))
            .build()
    }
}
