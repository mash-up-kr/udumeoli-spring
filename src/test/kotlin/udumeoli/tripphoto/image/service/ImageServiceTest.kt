package udumeoli.tripphoto.image.service

import io.mockk.Called
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import udumeoli.tripphoto.common.error.DomainException
import udumeoli.tripphoto.common.error.ErrorCode
import udumeoli.tripphoto.config.StorageProperties
import udumeoli.tripphoto.image.entity.Image
import udumeoli.tripphoto.image.repository.ImageRepository
import udumeoli.tripphoto.image.storage.ImageStoragePort
import udumeoli.tripphoto.image.storage.StoredObjectMeta
import udumeoli.tripphoto.image.thumbnail.ThumbnailPort

class ImageServiceTest {
    private val imageRepository = mockk<ImageRepository>()
    private val storagePort = mockk<ImageStoragePort>()
    private val thumbnailPort = mockk<ThumbnailPort>(relaxUnitFun = true)
    private val properties =
        StorageProperties(
            endpoint = "http://localhost:9000",
            region = "us-east-1",
            bucket = "test-bucket",
            accessKey = "test",
            secretKey = "test",
            publicBaseUrl = "https://cdn.example.com",
            upload = StorageProperties.UploadPolicy(maxSizeBytes = 1024),
        )
    private val imageService = ImageService(imageRepository, storagePort, thumbnailPort, properties)

    @Test
    fun `issueUploadUrl - image 행을 먼저 만들고 presigned URL을 반환한다`() {
        val savedImage = slot<Image>()
        every { storagePort.publicUrl(any()) } answers { "https://cdn.example.com/${firstArg<String>()}" }
        every { imageRepository.save(capture(savedImage)) } answers { firstArg<Image>().copy(id = 1L) }
        every { storagePort.issueUploadUrl(any(), "image/jpeg") } returns "https://upload.example.com/presigned"

        val target = imageService.issueUploadUrl("image/jpeg")

        assertThat(target.imageId).isEqualTo(1L)
        assertThat(target.uploadUrl).isEqualTo("https://upload.example.com/presigned")
        assertThat(savedImage.captured.objectKey).matches("original/[0-9a-f\\-]{36}\\.jpg")
        assertThat(savedImage.captured.originalUrl)
            .isEqualTo("https://cdn.example.com/${savedImage.captured.objectKey}")
    }

    @Test
    fun `issueUploadUrl - 허용 목록 밖 contentType은 VALIDATION_ERROR (SVG는 XSS 벡터라 제외)`() {
        assertThatThrownBy { imageService.issueUploadUrl("image/svg+xml") }
            .isInstanceOfSatisfying(DomainException::class.java) {
                assertThat(it.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
            }
        verify(exactly = 0) { imageRepository.save(any()) }
    }

    @Test
    fun `verifyUploaded - 업로드 확인된 이미지를 요청 순서대로 반환한다`() {
        every { imageRepository.findAllById(listOf(2L, 1L)) } returns listOf(image(1L), image(2L))
        every { storagePort.head(any()) } returns StoredObjectMeta(contentLength = 512, contentType = "image/jpeg")

        val result = imageService.verifyUploaded(listOf(2L, 1L))

        assertThat(result.map { it.id }).containsExactly(2L, 1L)
    }

    @Test
    fun `verifyUploaded - 존재하지 않는 imageId면 IMAGE_NOT_FOUND`() {
        every { imageRepository.findAllById(listOf(1L, 99L)) } returns listOf(image(1L))

        assertThatThrownBy { imageService.verifyUploaded(listOf(1L, 99L)) }
            .isInstanceOfSatisfying(DomainException::class.java) {
                assertThat(it.code).isEqualTo(ErrorCode.IMAGE_NOT_FOUND)
            }
    }

    @Test
    fun `verifyUploaded - 행은 있지만 객체가 없으면(미업로드) IMAGE_NOT_UPLOADED`() {
        every { imageRepository.findAllById(listOf(1L)) } returns listOf(image(1L))
        every { storagePort.head("original/1.jpg") } returns null

        assertThatThrownBy { imageService.verifyUploaded(listOf(1L)) }
            .isInstanceOfSatisfying(DomainException::class.java) {
                assertThat(it.code).isEqualTo(ErrorCode.IMAGE_NOT_UPLOADED)
            }
    }

    @Test
    fun `verifyUploaded - 크기 초과면 VALIDATION_ERROR를 던지고 객체를 회수한다`() {
        every { imageRepository.findAllById(listOf(1L)) } returns listOf(image(1L))
        every { storagePort.head("original/1.jpg") } returns
            StoredObjectMeta(contentLength = 2048, contentType = "image/jpeg")
        justRun { storagePort.delete(any()) }

        assertThatThrownBy { imageService.verifyUploaded(listOf(1L)) }
            .isInstanceOfSatisfying(DomainException::class.java) {
                assertThat(it.code).isEqualTo(ErrorCode.VALIDATION_ERROR)
            }
        verify { storagePort.delete(listOf("original/1.jpg")) }
    }

    @Test
    fun `deleteImages - 객체(원본+썸네일)를 먼저 지우고 행을 지운다 (중간 실패 시 고아 배치가 재시도)`() {
        val withThumbnail = image(1L).copy(thumbnailUrl = "https://cdn.example.com/thumb_1_123.jpg")
        every { imageRepository.findAllById(listOf(1L, 2L)) } returns listOf(withThumbnail, image(2L))
        justRun { storagePort.delete(any()) }
        justRun { imageRepository.deleteAllById(any()) }

        imageService.deleteImages(listOf(1L, 2L))

        verifyOrder {
            storagePort.delete(listOf("original/1.jpg", "thumb_1_123.jpg", "original/2.jpg"))
            imageRepository.deleteAllById(listOf(1L, 2L))
        }
    }

    @Test
    fun `deleteImages - 우리 스토리지 밖 thumbnailUrl은 삭제 대상에서 제외한다`() {
        val foreignThumbnail = image(1L).copy(thumbnailUrl = "https://other.example.com/thumb.jpg")
        every { imageRepository.findAllById(listOf(1L)) } returns listOf(foreignThumbnail)
        justRun { storagePort.delete(any()) }
        justRun { imageRepository.deleteAllById(any()) }

        imageService.deleteImages(listOf(1L))

        verify { storagePort.delete(listOf("original/1.jpg")) }
    }

    @Test
    fun `deleteImages - 빈 목록이면 아무것도 하지 않는다`() {
        imageService.deleteImages(emptyList())

        verify { listOf(imageRepository, storagePort) wasNot Called }
    }

    @Test
    fun `cleanUpOrphans - 고아 이미지의 객체와 행을 지우고 개수를 반환한다`() {
        every { imageRepository.findOrphansCreatedBefore(any()) } returns listOf(image(1L), image(2L))
        justRun { storagePort.delete(any()) }
        justRun { imageRepository.deleteAllById(any()) }

        val deleted = imageService.cleanUpOrphans()

        assertThat(deleted).isEqualTo(2)
        verifyOrder {
            storagePort.delete(listOf("original/1.jpg", "original/2.jpg"))
            imageRepository.deleteAllById(listOf(1L, 2L))
        }
    }

    @Test
    fun `cleanUpOrphans - 고아가 없으면 스토리지를 건드리지 않는다`() {
        every { imageRepository.findOrphansCreatedBefore(any()) } returns emptyList()

        assertThat(imageService.cleanUpOrphans()).isZero()
        verify { storagePort wasNot Called }
    }

    @Test
    fun `requestThumbnails - 이미지 id와 원본 URL로 요청한다 (썸네일 서버가 GET으로 내려받을 주소)`() {
        imageService.requestThumbnails(listOf(image(1L)))

        verify { thumbnailPort.requestThumbnail(1L, "https://cdn.example.com/original/1.jpg") }
    }

    @Test
    fun `requestThumbnails - 썸네일 요청 실패는 호출자에게 전파되지 않는다 (폴백 있는 작업)`() {
        every { thumbnailPort.requestThumbnail(any(), any()) } throws IllegalStateException("thumbnail server down")

        imageService.requestThumbnails(listOf(image(1L)))
    }

    private fun image(id: Long): Image =
        Image(
            id = id,
            objectKey = "original/$id.jpg",
            originalUrl = "https://cdn.example.com/original/$id.jpg",
        )
}
