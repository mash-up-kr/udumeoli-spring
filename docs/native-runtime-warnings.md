# Native Image 시작 경고

운영 Native Image에서는 Flyway의 클래스패스 스캐너가 `resource:` 프로토콜을 지원하지 않아 `Unable to scan location: /db/migration` 경고를 출력할 수 있다. Spring Boot의 Native Image 지원이 별도 방식으로 SQL 마이그레이션을 등록하므로, 이 경고만으로 마이그레이션이 실패한 것은 아니다. 실제 적용 여부는 시작 로그의 Flyway 버전과 `flyway_schema_history`에서 확인한다.

Micrometer의 `JvmGcMetrics`는 Native Image의 GC MXBean이 알림을 제공하지 않을 때 `GC notifications will not be available` 경고를 출력한다. 이때 GC 알림 기반 메트릭은 사용할 수 없지만 앱의 GC와 요청 처리는 계속 동작한다.

두 경고는 운영 로그와 Discord WARN 알림에서 제외했다. 두 클래스의 `ERROR` 로그 및 다른 Flyway·Micrometer 경고는 그대로 기록된다.
