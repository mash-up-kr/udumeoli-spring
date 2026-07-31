package udumeoli.tripphoto.image.graphql

import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.ContextValue
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.stereotype.Controller
import udumeoli.tripphoto.common.graphql.requireCurrentUserId
import udumeoli.tripphoto.config.CurrentUserGraphQlInterceptor
import udumeoli.tripphoto.image.dto.CreateImageUploadUrlInput
import udumeoli.tripphoto.image.dto.ImageUploadTarget
import udumeoli.tripphoto.image.service.ImageService

@Controller
class ImageGraphQlController(
    private val imageService: ImageService,
) {
    @MutationMapping
    fun createImageUploadUrl(
        @ContextValue(
            name = CurrentUserGraphQlInterceptor.CURRENT_USER_ID_CONTEXT_KEY,
            required = false,
        )
        currentUserId: Long?,
        @Argument input: CreateImageUploadUrlInput,
    ): ImageUploadTarget = imageService.createUploadUrl(requireCurrentUserId(currentUserId), input.contentType)
}
