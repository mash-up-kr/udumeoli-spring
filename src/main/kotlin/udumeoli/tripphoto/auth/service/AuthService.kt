package udumeoli.tripphoto.auth.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import udumeoli.tripphoto.auth.dto.AuthFlowStatus
import udumeoli.tripphoto.auth.dto.LoginCodeExchangeResponse
import udumeoli.tripphoto.auth.dto.TokenResponse
import udumeoli.tripphoto.auth.entity.RefreshToken
import udumeoli.tripphoto.auth.repository.RefreshTokenRepository
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
) {
    @Transactional
    fun prepareOAuthLogin(profile: SocialProfile): String {
        val socialAccount = socialAccountRepository.findByProviderAndProviderUserId(profile.provider, profile.providerUserId)
        val result =
            if (socialAccount == null) { // 신규 회원일 시 임시 토큰 발급
                LoginCodeExchangeResponse(
                    status = AuthFlowStatus.SIGNUP_REQUIRED,
                    signupToken = jwtTokenService.issueSignupToken(profile),
                )
            } else {
                LoginCodeExchangeResponse(
                    status = AuthFlowStatus.AUTHENTICATED,
                    tokens = issueAndStoreTokenPair(socialAccount.serviceUserId),
                )
            }
        return loginCodeStore.issue(result)
    }

    fun exchangeLoginCode(code: String): LoginCodeExchangeResponse = loginCodeStore.consume(code)

    @Transactional
    fun completeSignup(
        signupToken: String,
        nickname: String,
    ): TokenResponse {
        val normalizedNickname = nickname.trim()
        if (normalizedNickname.isEmpty() || normalizedNickname.length > MAX_NICKNAME_LENGTH) {
            throw AuthException(AuthErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "닉네임은 1자 이상 40자 이하로 입력해주세요.")
        }

        val profile = jwtTokenService.decodeSignupToken(signupToken)
        if (socialAccountRepository.findByProviderAndProviderUserId(profile.provider, profile.providerUserId) != null) {
            throw AuthException(AuthErrorCode.SIGNUP_ALREADY_COMPLETED, HttpStatus.CONFLICT, "이미 가입이 완료된 소셜 계정입니다.")
        }

        try {
            val user =
                serviceUserRepository.save(
                    ServiceUser(
                        nickname = normalizedNickname,
                        profileImageUrl = profile.profileImageUrl ?: "DEFAULT",
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
            return issueAndStoreTokenPair(userId)
        } catch (exception: DataIntegrityViolationException) {
            throw AuthException(AuthErrorCode.SIGNUP_ALREADY_COMPLETED, HttpStatus.CONFLICT, "이미 가입이 완료된 소셜 계정입니다.", exception)
        }
    }

    @Transactional
    fun refresh(refreshToken: String): TokenResponse {
        val claims = jwtTokenService.decodeRefreshToken(refreshToken)
        val storedToken =
            refreshTokenRepository.findByTokenHash(hash(refreshToken))
                ?: throw AuthException(AuthErrorCode.INVALID_TOKEN, HttpStatus.UNAUTHORIZED, "이미 사용되었거나 폐기된 refresh token입니다.")

        if (storedToken.serviceUserId != claims.serviceUserId || storedToken.expiresAt.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            refreshTokenRepository.delete(storedToken)
            throw AuthException(AuthErrorCode.INVALID_TOKEN, HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 refresh token입니다.")
        }
        if (!serviceUserRepository.existsById(claims.serviceUserId)) {
            refreshTokenRepository.delete(storedToken)
            throw AuthException(AuthErrorCode.USER_NOT_FOUND, HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다.")
        }

        if (refreshTokenRepository.consume(requireNotNull(storedToken.id), storedToken.tokenHash) != 1) {
            throw AuthException(AuthErrorCode.INVALID_TOKEN, HttpStatus.UNAUTHORIZED, "이미 사용되었거나 폐기된 refresh token입니다.")
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

    private fun hash(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_NICKNAME_LENGTH = 40
    }
}
