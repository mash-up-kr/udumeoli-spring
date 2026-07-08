package udumeoli.tripphoto.image.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.tripphoto.image.entity.Image

interface ImageRepository : ListCrudRepository<Image, Long> {
    fun findAllByUploaderId(uploaderId: Long): List<Image>
}
