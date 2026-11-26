@file:Suppress("LongParameterList", "TooManyFunctions", "ThrowsCount", "MaxLineLength", "MagicNumber", "ForbiddenComment")

package udumeoli.tripphoto.auth.service

import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.auth.dto.AuthFlowStatus
import udumeoli.tripphoto.auth.dto.LoginCodeExchangeResponse
import udumeoli.tripphoto.auth.dto.TokenResponse
import udumeoli.tripphoto.auth.entity.RefreshToken
import udumeoli.tripphoto.auth.repository.RefreshTokenRepository
import udumeoli.tripphoto.image.dto.ImageUploadTarget
import udumeoli.tripphoto.image.service.ImageService
import udumeoli.tripphoto.kpi.entity.KpiEventType
import udumeoli.tripphoto.kpi.event.KpiEventOccurred
import udumeoli.tripphoto.user.entity.Nickname
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.entity.SocialAccount
import udumeoli.tripphoto.user.repository.ServiceUserRepository
import udumeoli.tripphoto.user.repository.SocialAccountRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class AuthService(
    private val serviceUserRepository: ServiceUserRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtTokenService: JwtTokenService,
    private val loginCodeStore: OAuthLoginCodeStore,
    private val imageService: ImageService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun createSignupImageUploadUrl(
        signupToken: String,
        contentType: String,
    ): ImageUploadTarget {
        jwtTokenService.decodeSignupToken(signupToken)
        return runCatching { imageService.createUploadUrl(uploaderId = null, contentType = contentType) }
            .getOrElse {
                throw AuthException(AuthErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "error.image.invalid-format", it)
            }
    }

    @Transactional
    fun prepareOAuthLogin(profile: SocialProfile): String {
        val socialAccount = socialAccountRepository.findByProviderAndProviderUserId(profile.provider, profile.providerUserId)
        val payload =
            if (socialAccount == null) {
                LoginCodePayload.SignupRequired(
                    provider = profile.provider,
                    providerUserId = profile.providerUserId,
                )
            } else {
                LoginCodePayload.Authenticated(serviceUserId = socialAccount.serviceUserId)
            }
        return loginCodeStore.issue(payload)
    }

    @Transactional
    fun exchangeLoginCode(code: String): LoginCodeExchangeResponse =
        when (val payload = loginCodeStore.consume(code)) {
            is LoginCodePayload.Authenticated ->
                LoginCodeExchangeResponse(
                    status = AuthFlowStatus.AUTHENTICATED,
                    tokens = issueAndStoreTokenPair(payload.serviceUserId),
                )

            is LoginCodePayload.SignupRequired ->
                LoginCodeExchangeResponse(
                    status = AuthFlowStatus.SIGNUP_REQUIRED,
                    signupToken =
                        jwtTokenService.issueSignupToken(
                            SocialProfile(
                                provider = payload.provider,
                                providerUserId = payload.providerUserId,
                            ),
                        ),
                )
        }

    @Suppress("ForbiddenComment")
    @Transactional
    fun completeSignup(
        signupToken: String,
        nickname: String,
        profileImage: Long,
    ): TokenResponse {
        val normalizedNickname =
            Nickname.normalizeOrNull(nickname)
                ?: throw AuthException(
                    AuthErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "error.nickname.invalid",
                    messageArgs = arrayOf(Nickname.MAX_LENGTH),
                )

        validateImageExists(profileImage)
        // TODO: 업로드한 프로필 사진(프리셋 아님)이면 imageService.requestThumbnails(...)로 썸네일 생성 요청

        val profile = jwtTokenService.decodeSignupToken(signupToken)
        if (socialAccountRepository.findByProviderAndProviderUserId(profile.provider, profile.providerUserId) != null) {
            throw AuthException(AuthErrorCode.SIGNUP_ALREADY_COMPLETED, HttpStatus.CONFLICT, "error.signup.already-completed")
        }

        try {
            val user =
                serviceUserRepository.save(
                    ServiceUser(
                        nickname = normalizedNickname,
                        profileImage = profileImage,
                    ),
                )
            val userId = requireNotNull(user.id)
            socialAccountRepository.save(
                SocialAccount(
                    serviceUserId = userId,
                    provider = profile.provider,
                    providerUserId = profile.providerUserId,
                    providerEmail = profile.email,
                ),
            )
            eventPublisher.publishEvent(
                KpiEventOccurred(
                    type = KpiEventType.SIGNUP,
                    serviceUserId = userId,
                    metadata = """{"provider":"${profile.provider}"}""",
                ),
            )
            return issueAndStoreTokenPair(userId)
        } catch (exception: DataIntegrityViolationException) {
            throw AuthException(AuthErrorCode.SIGNUP_ALREADY_COMPLETED, HttpStatus.CONFLICT, "error.signup.already-completed", exception)
        }
    }

    @Transactional
    fun refresh(refreshToken: String): TokenResponse {
        val claims = jwtTokenService.decodeRefreshToken(refreshToken)
        val storedToken =
            refreshTokenRepository.findByTokenHash(hash(refreshToken))
                ?: throw AuthException(AuthErrorCode.INVALID_TOKEN, HttpStatus.UNAUTHORIZED, "error.auth.refresh-token-revoked")

        if (storedToken.serviceUserId != claims.serviceUserId || storedToken.expiresAt.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            refreshTokenRepository.delete(storedToken)
            throw AuthException(AuthErrorCode.INVALID_TOKEN, HttpStatus.UNAUTHORIZED, "error.auth.refresh-token-invalid")
        }
        if (!serviceUserRepository.existsById(claims.serviceUserId)) {
            refreshTokenRepository.delete(storedToken)
            throw AuthException(AuthErrorCode.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED, "error.user.not-found")
        }

        if (refreshTokenRepository.consume(requireNotNull(storedToken.id), storedToken.tokenHash) != 1) {
            throw AuthException(AuthErrorCode.INVALID_TOKEN, HttpStatus.UNAUTHORIZED, "error.auth.refresh-token-revoked")
        }
        return issueAndStoreTokenPair(claims.serviceUserId)
    }

    @Transactional
    fun logout(refreshToken: String) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken))?.let(refreshTokenRepository::delete)
    }

    private fun issueAndStoreTokenPair(serviceUserId: Long): TokenResponse {
        val issued = jwtTokenService.issueTokenPair(serviceUserId)
        refreshTokenRepository.save(
            RefreshToken(
                serviceUserId = serviceUserId,
                tokenHash = hash(issued.response.refreshToken),
                expiresAt = LocalDateTime.ofInstant(issued.refreshExpiresAt, ZoneOffset.UTC),
            ),
        )
        return issued.response
    }

    private fun validateImageExists(imageId: Long) {
        if (ServiceUser.isPresetProfileImage(imageId)) return
        if (imageService.findImageOrNull(imageId) == null) {
            throw AuthException(AuthErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "error.image.not-found")
        }
    }

    private fun hash(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
