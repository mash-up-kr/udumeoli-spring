package udumeoli.tripphoto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository

@SpringBootTest
@ActiveProfiles("local")
class AuditingTest {
    @Autowired
    lateinit var serviceUserRepository: ServiceUserRepository

    @Test
    fun `INSERT 시 createdAt·updatedAt을 앱이 채운다`() {
        val saved = serviceUserRepository.save(ServiceUser(nickname = "감사테스트-insert"))

        assertThat(saved.auditMetadata.createdAt)
            .withFailMessage("createdAt이 null → @CreatedDate/@EnableJdbcAuditing이 동작하지 않는다")
            .isNotNull()
        assertThat(saved.auditMetadata.updatedAt)
            .withFailMessage("updatedAt이 null → @LastModifiedDate/@EnableJdbcAuditing이 동작하지 않는다")
            .isNotNull()
    }

    @Test
    fun `UPDATE 시 updatedAt은 갱신되고 createdAt은 보존된다`() {
        val saved = serviceUserRepository.save(ServiceUser(nickname = "a"))
        val firstUpdatedAt =
            requireNotNull(saved.auditMetadata.updatedAt) {
                "INSERT 직후 updatedAt이 null → auditing 자체가 동작하지 않는다"
            }

        Thread.sleep(1000)

        val updated = serviceUserRepository.save(saved.copy(nickname = "b"))

        assertThat(updated.auditMetadata.updatedAt)
            .withFailMessage {
                "updatedAt이 갱신되지 않았다 (before=$firstUpdatedAt, after=${updated.auditMetadata.updatedAt}) " +
                    "→ auditing이 UPDATE에 걸리지 않는다"
            }.isAfter(firstUpdatedAt)

        assertThat(updated.auditMetadata.createdAt)
            .withFailMessage("createdAt이 UPDATE에서 바뀌었다 → @CreatedDate가 INSERT에만 걸려야 한다")
            .isEqualTo(saved.auditMetadata.createdAt)
    }
}
