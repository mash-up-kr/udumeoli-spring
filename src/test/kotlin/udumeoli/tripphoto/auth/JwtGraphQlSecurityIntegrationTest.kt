package udumeoli.tripphoto.auth

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import udumeoli.tripphoto.auth.repository.RefreshTokenRepository
import udumeoli.tripphoto.auth.service.JwtTokenService
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository
import udumeoli.tripphoto.user.repository.SocialAccountRepository

@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:jwtgraphql;MODE=Oracle;DB_CLOSE_DELAY=-1"])
@AutoConfigureMockMvc
@ActiveProfiles("local")
class JwtGraphQlSecurityIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtTokenService: JwtTokenService

    @Autowired lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired lateinit var serviceUserRepository: ServiceUserRepository

    @Autowired lateinit var socialAccountRepository: SocialAccountRepository

    @BeforeEach
    fun setUp() {
        cleanUp()
    }

    @AfterEach
    fun tearDown() {
        cleanUp()
    }

    private fun cleanUp() {
        refreshTokenRepository.deleteAll()
        socialAccountRepository.deleteAll()
        serviceUserRepository.deleteAll()
    }

    @Test
    fun `GraphQL은 Bearer access token의 사용자를 식별한다`() {
        val user = serviceUserRepository.save(ServiceUser(nickname = "JWT 회원", profileImage = 1L))
        val accessToken = jwtTokenService.issueTokenPair(requireNotNull(user.id)).response.accessToken

        mockMvc
            .post("/graphql") {
                header("Authorization", "Bearer $accessToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"query":"{ me { nickname } }"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.me.nickname") { value("JWT 회원") }
            }
    }

    @Test
    fun `Bearer access token이 없으면 GraphQL 요청을 거절한다`() {
        mockMvc
            .post("/graphql") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"query":"{ me { nickname } }"}"""
            }.andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("UNAUTHENTICATED") }
            }
    }
}
