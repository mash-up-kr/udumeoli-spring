package udumeoli.tripphoto.image.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.image.service.ImageUploadTarget

/**
 * 이미지 뮤테이션 리졸버. 메서드명이 schema.graphqls의 Mutation 필드명과 일치하면 자동 매핑된다.
 */
@Controller
class ImageMutationResolver(
    private val imageService: ImageService,
) {
    @MutationMapping
    fun createImageUploadUrl(
        @Argument input: CreateImageUploadUrlInput,
    ): ImageUploadTarget = imageService.issueUploadUrl(contentType = input.contentType)
}

/** schema.graphqls의 CreateImageUploadUrlInput과 1:1 */
data class CreateImageUploadUrlInput(
    val contentType: String,
)
