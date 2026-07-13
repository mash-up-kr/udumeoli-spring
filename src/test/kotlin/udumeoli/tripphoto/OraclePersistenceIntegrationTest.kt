package udumeoli.tripphoto

import org.junit.jupiter.api.Tag
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.oracle.OracleContainer

/**
 * 실제 Oracle(gvenzl/oracle-free)에 대한 통합 테스트 (M0-5).
 * H2 Oracle 모드가 잡지 못하는 실제 Oracle 문법/타입 차이를 검증한다.
 *
 * Docker가 필요하며 `./gradlew integrationTest`로 실행한다. CI에서는 pr.yml이 실행.
 * 데이터소스는 @ServiceConnection이 컨테이너 접속 정보로 자동 구성하고, Flyway V1도 컨테이너에 그대로 적용된다.
 */
@Tag("integration")
@Testcontainers
class OraclePersistenceIntegrationTest : AbstractPersistenceCrudTest() {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val oracle: OracleContainer = OracleContainer("gvenzl/oracle-free:23-slim-faststart")
    }
}
