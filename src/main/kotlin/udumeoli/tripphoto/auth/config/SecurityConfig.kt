package udumeoli.tripphoto.auth.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import udumeoli.tripphoto.auth.dto.AuthErrorResponse
import udumeoli.tripphoto.auth.service.JwtTokenService
import udumeoli.tripphoto.auth.web.FrontendCallbackOverrideFilter
import udumeoli.tripphoto.auth.web.OAuth2LoginFailureHandler
import udumeoli.tripphoto.auth.web.OAuth2LoginSuccessHandler

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        objectMapper: ObjectMapper,
        successHandler: OAuth2LoginSuccessHandler,
        failureHandler: OAuth2LoginFailureHandler,
        frontendCallbackOverrideFilter: FrontendCallbackOverrideFilter,
    ): SecurityFilterChain =
        http
            .addFilterBefore(frontendCallbackOverrideFilter, OAuth2AuthorizationRequestRedirectFilter::class.java)
            .cors { }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/auth/**",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/actuator/health",
                        "/graphiql/**",
                        "/h2-console/**",
                        "/error",
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2Login {
                it
                    .successHandler(successHandler)
                    .failureHandler(failureHandler)
            }.oauth2ResourceServer {
                it
                    .jwt { jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()) }
                    .authenticationEntryPoint { _, response, _ ->
                        writeError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다.")
                    }.accessDeniedHandler { _, response, _ ->
                        writeError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.")
                    }
            }.headers { it.frameOptions { frame -> frame.sameOrigin() } }
            .build()

    @Bean
    fun jwtDecoder(jwtTokenService: JwtTokenService): JwtDecoder = jwtTokenService.accessTokenDecoder()

    @Bean
    fun corsConfigurationSource(properties: AuthProperties): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = properties.corsAllowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type")
                exposedHeaders = listOf("Location")
                allowCredentials = true
                maxAge = 3600
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
    }

    private fun jwtAuthenticationConverter(): JwtAuthenticationConverter =
        JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter { jwt: Jwt ->
                jwt.getClaimAsStringList("roles").orEmpty().map { SimpleGrantedAuthority("ROLE_$it") }
            }
        }

    private fun writeError(
        response: HttpServletResponse,
        objectMapper: ObjectMapper,
        status: Int,
        code: String,
        message: String,
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.outputStream, AuthErrorResponse(code, message))
    }
}
