package udumeoli.tripphoto.auth.dto

import jakarta.validation.constraints.NotBlank

data class GoogleLoginRequest(
    @field:NotBlank
    val idToken: String,
)

data class AppleLoginRequest(
    @field:NotBlank
    val identityToken: String,
    val authorizationCode: String? = null,
)

data class KakaoLoginRequest(
    @field:NotBlank
    val accessToken: String,
)
