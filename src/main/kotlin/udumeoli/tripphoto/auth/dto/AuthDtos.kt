package udumeoli.tripphoto.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class ExchangeLoginCodeRequest(
    @field:NotBlank
    val code: String,
)

data class CompleteSignupRequest(
    @field:NotBlank
    val signupToken: String,
    @field:NotBlank
    @field:Size(max = 6)
    val nickname: String,
    @field:NotNull
    val profileImage: Long?,
)

data class SignupImageUploadUrlRequest(
    @field:NotBlank
    val signupToken: String,
    @field:NotBlank
    val contentType: String,
)

data class RefreshAccessTokenRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class LogoutRequest(
    @field:NotBlank
    val refreshToken: String,
)

enum class AuthFlowStatus {
    AUTHENTICATED,
    SIGNUP_REQUIRED,
}

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val accessTokenExpiresIn: Long,
)

data class LoginCodeExchangeResponse(
    val status: AuthFlowStatus,
    val tokens: TokenResponse? = null,
    val signupToken: String? = null,
)

data class AuthErrorResponse(
    val code: String,
    val message: String,
)
