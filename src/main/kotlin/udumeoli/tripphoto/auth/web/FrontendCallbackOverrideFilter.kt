package udumeoli.tripphoto.auth.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import udumeoli.tripphoto.auth.config.AuthProperties

const val FRONTEND_CALLBACK_SESSION_ATTRIBUTE = "FRONTEND_CALLBACK_URL_OVERRIDE"
const val FRONTEND_REDIRECT_PARAM = "frontendRedirect"

/**
 * /oauth2/authorization/{registrationId} 요청에 frontendRedirect 쿼리파라미터가 있고
 * 그 값이 [AuthProperties.allowedFrontendCallbackUrls]에 있으면 세션에 저장해둔다.
 * 카카오 콜백 처리 후 [OAuth2LoginSuccessHandler]/[OAuth2LoginFailureHandler]가 이 값을 읽어
 * 기본 frontendCallbackUrl 대신 사용한다.
 *
 * 검증 없이 아무 URL이나 받으면 오픈 리다이렉트 취약점이 되므로, 반드시 화이트리스트에 있는 값만 허용한다.
 */
@Component
class FrontendCallbackOverrideFilter(
    private val properties: AuthProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.requestURI.startsWith(AUTHORIZATION_REQUEST_PREFIX)) {
            val requested = request.getParameter(FRONTEND_REDIRECT_PARAM)
            if (requested != null && requested in properties.allowedFrontendCallbackUrls) {
                request.session.setAttribute(FRONTEND_CALLBACK_SESSION_ATTRIBUTE, requested)
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private const val AUTHORIZATION_REQUEST_PREFIX = "/oauth2/authorization/"
    }
}
