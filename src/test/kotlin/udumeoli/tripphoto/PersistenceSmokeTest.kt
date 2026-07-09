package udumeoli.tripphoto

import org.springframework.test.context.ActiveProfiles

/**
 * H2(MODE=Oracle) 스모크 테스트 — Docker 없이 `./gradlew test`로 빠르게 돈다.
 * 실제 Oracle 검증은 [OraclePersistenceIntegrationTest] 참고.
 */
@ActiveProfiles("local")
class PersistenceSmokeTest : AbstractPersistenceCrudTest()
