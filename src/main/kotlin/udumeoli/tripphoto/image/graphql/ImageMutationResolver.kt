package udumeoli.tripphoto.image.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.image.service.ImageUploadTarget

@Controller
class ImageMutationResolver(
    private val imageService: ImageService,
) {
    @MutationMapping
    fun createImageUploadUrl(
        @Argument input: CreateImageUploadUrlInput,
    ): ImageUploadTarget = imageService.createUploadUrl(contentType = input.contentType)
}

data class CreateImageUploadUrlInput(
    val contentType: String,
)
