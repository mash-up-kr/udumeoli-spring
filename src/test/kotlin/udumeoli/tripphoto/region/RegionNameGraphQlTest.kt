package udumeoli.tripphoto.region

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.MessageSource
import org.springframework.graphql.ExecutionGraphQlService
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester
import org.springframework.http.MediaType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import udumeoli.tripphoto.region.repository.RegionRepository
import java.util.Locale

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RegionNameGraphQlTest {
    @Autowired lateinit var graphQlService: ExecutionGraphQlService

    @Autowired lateinit var regionRepository: RegionRepository

    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var messageSource: MessageSource

    @AfterEach
    fun clearAuthentication() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `region catalog returns Korean and English names for every seeded region`() {
        assertThat(regionRepository.findAll()).allSatisfy { region ->
            assertThat(region.nameEng).isNotBlank()
        }
        val query = "query { regions { regionCode name nameKo nameEn } }"

        tester(Locale.KOREAN)
            .document(query)
            .execute()
            .path("regions")
            .entityList(Any::class.java)
            .hasSize(168)
            .path("regions[?(@.regionCode == '38110')].name")
            .entityList(String::class.java)
            .containsExactly("창원시")

        tester(Locale.ENGLISH)
            .document(query)
            .execute()
            .path("regions[?(@.regionCode == '38110')].name")
            .entityList(String::class.java)
            .containsExactly("Changwon-si")
            .path("regions[?(@.regionCode == '38110')].nameEn")
            .entityList(String::class.java)
            .containsExactly("Changwon-si")
    }

    @Test
    fun `Accept-Language selects English on the HTTP GraphQL endpoint`() {
        mockMvc
            .perform(
                post("/graphql")
                    .with(authentication(TestingAuthenticationToken("1", null, emptyList())))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept-Language", "en-US")
                    .content("""{"query":"query { regions { regionCode name } }"}"""),
            ).andExpect(jsonPath("$.data.regions[?(@.regionCode == '38110')].name").value("Changwon-si"))
    }

    @Test
    fun `Accept-Language translates REST authentication errors`() {
        mockMvc
            .perform(
                post("/api/auth/exchange")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept-Language", "en-US")
                    .content("""{"code":"invalid"}"""),
            ).andExpect(jsonPath("$.message").value("This login code is invalid or expired."))
    }

    @Test
    fun `message bundles resolve Korean English and parameterized errors`() {
        assertThat(messageSource.getMessage("error.region.not-found", arrayOf("38110"), Locale.KOREAN))
            .isEqualTo("존재하지 않는 지역입니다: 38110")
        assertThat(messageSource.getMessage("error.region.not-found", arrayOf("38110"), Locale.ENGLISH))
            .isEqualTo("Region not found: 38110")
        assertThat(messageSource.getMessage("error.auth.google-token-invalid", null, Locale.KOREAN))
            .isEqualTo("Google 인증 토큰이 유효하지 않습니다.")
    }

    private fun tester(locale: Locale): ExecutionGraphQlServiceTester {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("1", null, emptyList())
        return ExecutionGraphQlServiceTester
            .builder(graphQlService)
            .configureExecutionInput { _, builder -> builder.locale(locale).build() }
            .build()
    }
}
