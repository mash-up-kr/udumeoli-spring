package udumeoli.tripphoto.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
class StorageConfig {
    @Bean
    fun s3Client(properties: StorageProperties): S3Client =
        S3Client
            .builder()
            .endpointOverride(URI.create(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(staticCredentials(properties))
            .forcePathStyle(true)
            .httpClient(UrlConnectionHttpClient.create())
            .build()

    @Bean
    fun s3Presigner(properties: StorageProperties): S3Presigner =
        S3Presigner
            .builder()
            .endpointOverride(URI.create(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(staticCredentials(properties))
            .serviceConfiguration(
                S3Configuration
                    .builder()
                    .pathStyleAccessEnabled(true)
                    .build(),
            ).build()

    private fun staticCredentials(properties: StorageProperties): StaticCredentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey, properties.secretKey),
        )
}
