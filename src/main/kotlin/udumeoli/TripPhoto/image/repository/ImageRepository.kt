package udumeoli.TripPhoto.image.repository

import org.springframework.data.repository.ListCrudRepository
import udumeoli.TripPhoto.image.entity.Image

interface ImageRepository : ListCrudRepository<Image, Long> {
    fun findAllByUploaderId(uploaderId: Long): List<Image>
}
