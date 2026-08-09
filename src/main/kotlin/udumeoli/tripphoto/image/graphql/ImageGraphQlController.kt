package udumeoli.tripphoto.image.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.auth.annotation.LoginUser
import udumeoli.tripphoto.image.dto.CreateImageUploadUrlInput
import udumeoli.tripphoto.image.dto.ImageUploadTarget
import udumeoli.tripphoto.image.service.ImageService

@Controller
class ImageGraphQlController(
    private val imageService: ImageService,
) {
    @MutationMapping
    fun createImageUploadUrl(
        @LoginUser currentUserId: Long,
        @Argument input: CreateImageUploadUrlInput,
    ): ImageUploadTarget = imageService.createUploadUrl(currentUserId, input.contentType)
}
