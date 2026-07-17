package udumeoli.tripphoto

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TripPhotoApplication

fun main(args: Array<String>) {
    runApplication<TripPhotoApplication>(*args)
}
