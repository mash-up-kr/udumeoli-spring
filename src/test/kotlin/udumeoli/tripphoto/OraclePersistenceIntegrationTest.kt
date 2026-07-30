package udumeoli.tripphoto

import org.junit.jupiter.api.Tag
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.oracle.OracleContainer

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
