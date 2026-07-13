package udumeoli.tripphoto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import udumeoli.tripphoto.user.entity.ServiceUser
import udumeoli.tripphoto.user.repository.ServiceUserRepository

/**
 * @EnableJdbcAuditing이 실제로 동작하는지(= 애플리케이션이 created_at/updated_at을 관리하는지) 검증한다.
 *
 * 핵심 관심사: Oracle에는 MySQL의 ON UPDATE CURRENT_TIMESTAMP가 없다.
 * 따라서 updated_at 갱신은 전적으로 Spring Data JDBC auditing(@LastModifiedDate)에 의존한다.
 * auditing이 꺼져 있거나 @Embedded 필드를 채우지 못하면 UPDATE 검증이 깨진다.
 *
 * DB 독립적인 앱 레이어 동작이므로 빠른 H2(MODE=Oracle)로 검증한다.
 */
@SpringBootTest
@ActiveProfiles("local")
class AuditingTest {
    @Autowired
    lateinit var serviceUserRepository: ServiceUserRepository

    @Test
    fun `INSERT 시 createdAt·updatedAt을 앱이 채운다`() {
        val saved = serviceUserRepository.save(ServiceUser(nickname = "감사테스트-insert"))

        // auditing이 꺼져 있으면 엔티티 필드는 null로 남는다.
        // (DDL DEFAULT CURRENT_TIMESTAMP는 DB 컬럼만 채우고, save()가 돌려주는 객체엔 반영되지 않는다)
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

        Thread.sleep(1000) // 초 단위 절삭에도 확실히 뒤가 되도록 1초 벌린다

        val updated = serviceUserRepository.save(saved.copy(nickname = "b"))

        // 이게 깨지면 UPDATE 때 @LastModifiedDate가 안 걸리는 것.
        // Oracle엔 ON UPDATE CURRENT_TIMESTAMP가 없으므로 updated_at이 INSERT 시점 값으로 박제된다.
        assertThat(updated.auditMetadata.updatedAt)
            .withFailMessage {
                "updatedAt이 갱신되지 않았다 (before=$firstUpdatedAt, after=${updated.auditMetadata.updatedAt}) " +
                    "→ auditing이 UPDATE에 걸리지 않는다"
            }.isAfter(firstUpdatedAt)

        // @CreatedDate는 INSERT에만 걸려야 한다 → UPDATE에서 바뀌면 안 된다
        assertThat(updated.auditMetadata.createdAt)
            .withFailMessage("createdAt이 UPDATE에서 바뀌었다 → @CreatedDate가 INSERT에만 걸려야 한다")
            .isEqualTo(saved.auditMetadata.createdAt)
    }
}
