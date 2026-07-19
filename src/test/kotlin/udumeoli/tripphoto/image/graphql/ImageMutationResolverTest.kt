package udumeoli.tripphoto.image.graphql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest
import org.springframework.context.annotation.Import
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.context.bean.override.mockito.MockitoBean
import udumeoli.tripphoto.common.error.DomainException
import udumeoli.tripphoto.common.error.ErrorCode
import udumeoli.tripphoto.config.GraphQlConfig
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.image.service.ImageUploadTarget

/**
 * GraphQL 슬라이스 테스트 — 스키마 바인딩과 에러 규격(extensions.code)을 검증한다.
 * GraphQlConfig는 슬라이스 스캔에 포함되지 않아 명시적으로 Import한다
 * (커스텀 스칼라 Date/DateTime 와이어링이 없으면 schema.graphqls 빌드 자체가 실패).
 */
@GraphQlTest(ImageMutationResolver::class)
@Import(GraphQlConfig::class)
class ImageMutationResolverTest {
    @Autowired
    lateinit var graphQlTester: GraphQlTester

    @MockitoBean
    lateinit var imageService: ImageService

    @Test
    fun `createImageUploadUrl - imageId와 uploadUrl을 반환한다`() {
        given(imageService.createUploadUrl("image/jpeg"))
            .willReturn(ImageUploadTarget(imageId = 1L, uploadUrl = "https://upload.example.com/presigned"))

        graphQlTester
            .document(
                """
                mutation {
                    createImageUploadUrl(input: { contentType: "image/jpeg" }) {
                        imageId
                        uploadUrl
                    }
                }
                """.trimIndent(),
            ).execute()
            .path("createImageUploadUrl.imageId")
            .entity(String::class.java)
            .isEqualTo("1")
            .path("createImageUploadUrl.uploadUrl")
            .entity(String::class.java)
            .isEqualTo("https://upload.example.com/presigned")
    }

    @Test
    fun `createImageUploadUrl - 도메인 예외는 extensions_code로 내려간다`() {
        given(imageService.createUploadUrl("image/svg+xml"))
            .willThrow(DomainException(ErrorCode.VALIDATION_ERROR, "허용되지 않는 contentType"))

        graphQlTester
            .document(
                """
                mutation {
                    createImageUploadUrl(input: { contentType: "image/svg+xml" }) {
                        imageId
                    }
                }
                """.trimIndent(),
            ).execute()
            .errors()
            .satisfy { errors ->
                assertThat(errors).hasSize(1)
                assertThat(errors[0].extensions["code"]).isEqualTo("VALIDATION_ERROR")
            }
    }
}
