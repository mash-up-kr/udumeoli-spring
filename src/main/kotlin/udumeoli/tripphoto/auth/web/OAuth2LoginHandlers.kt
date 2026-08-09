package udumeoli.tripphoto.auth.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import udumeoli.tripphoto.auth.config.AuthProperties
import udumeoli.tripphoto.auth.service.AuthService
import udumeoli.tripphoto.auth.service.SocialProfile

@Component
class OAuth2LoginSuccessHandler(
    private val authService: AuthService,
    private val properties: AuthProperties,
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oauth = authentication as OAuth2AuthenticationToken
        val attributes = oauth.principal.attributes
        val profile = extractProfile(oauth.authorizedClientRegistrationId, attributes)
        val code = authService.prepareOAuthLogin(profile)
        SecurityContextHolder.clearContext()
        request.getSession(false)?.invalidate()
        response.sendRedirect(callbackUrl("code", code))
    }

    private fun extractProfile(
        provider: String,
        attributes: Map<String, Any>,
    ): SocialProfile {
        val providerUserId = requireNotNull(attributes["id"] ?: attributes["sub"]).toString()
        val account = attributes["kakao_account"] as? Map<*, *>
        val profile = account?.get("profile") as? Map<*, *>
        return SocialProfile(
            provider = provider.lowercase(),
            providerUserId = providerUserId,
            email = (account?.get("email") ?: attributes["email"]) as? String,
            profileImageUrl = (profile?.get("profile_image_url") ?: attributes["picture"]) as? String,
        )
    }

    private fun callbackUrl(
        name: String,
        value: String,
    ): String =
        UriComponentsBuilder
            .fromUriString(properties.frontendCallbackUrl)
            .queryParam(name, value)
            .build()
            .encode()
            .toUriString()
}

@Component
class OAuth2LoginFailureHandler(
    private val properties: AuthProperties,
) : AuthenticationFailureHandler {
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: org.springframework.security.core.AuthenticationException,
    ) {
        log.warn("OAuth2 login failed", exception)
        val redirectUrl =
            UriComponentsBuilder
                .fromUriString(properties.frontendCallbackUrl)
                .queryParam("error", "oauth_login_failed")
                .build()
                .encode()
                .toUriString()
        response.sendRedirect(redirectUrl)
    }

    companion object {
        private val log = LoggerFactory.getLogger(OAuth2LoginFailureHandler::class.java)
    }
}
