package udumeoli.tripphoto.auth.web

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import udumeoli.tripphoto.auth.dto.AuthErrorResponse
import udumeoli.tripphoto.auth.dto.CompleteSignupRequest
import udumeoli.tripphoto.auth.dto.ExchangeLoginCodeRequest
import udumeoli.tripphoto.auth.dto.LoginCodeExchangeResponse
import udumeoli.tripphoto.auth.dto.LogoutRequest
import udumeoli.tripphoto.auth.dto.RefreshAccessTokenRequest
import udumeoli.tripphoto.auth.dto.TokenResponse
import udumeoli.tripphoto.auth.service.AuthException
import udumeoli.tripphoto.auth.service.AuthService

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/exchange")
    fun exchange(
        @Valid @RequestBody request: ExchangeLoginCodeRequest,
    ): LoginCodeExchangeResponse = authService.exchangeLoginCode(request.code)

    @PostMapping("/signup")
    fun signup(
        @Valid @RequestBody request: CompleteSignupRequest,
    ): TokenResponse = authService.completeSignup(request.signupToken, request.nickname)

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshAccessTokenRequest,
    ): TokenResponse = authService.refresh(request.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
    ) {
        authService.logout(request.refreshToken)
    }
}

@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(AuthException::class)
    fun handle(exception: AuthException): org.springframework.http.ResponseEntity<AuthErrorResponse> =
        org.springframework.http.ResponseEntity
            .status(exception.status)
            .body(AuthErrorResponse(exception.code.name, exception.message))
}
