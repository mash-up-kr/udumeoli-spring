package udumeoli.tripphoto.auth.service

import org.springframework.http.HttpStatus

enum class AuthErrorCode {
    VALIDATION_ERROR,
    INVALID_LOGIN_CODE,
    INVALID_TOKEN,
    SIGNUP_ALREADY_COMPLETED,
    USER_NOT_FOUND,
}

class AuthException(
    val code: AuthErrorCode,
    val status: HttpStatus,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
