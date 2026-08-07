-- V5: 기존 스키마 전면 폐기
--   * V1~V4를 거치며 누적된 이름 변경/컬럼 이관이 많아, 개별 ALTER로 따라잡는 대신
--     스키마를 통째로 버리고 V6에서 현재 도메인 모델대로 다시 만든다.
--   * 개발 단계 DB 전제 — 이 시점의 모든 데이터는 폐기된다.
--   * flyway_schema_history는 Flyway가 실행 중 점유하므로 절대 드롭하지 않는다.
-- Oracle(ATP)과 H2(MODE=Oracle) 양쪽에서 동일하게 동작해야 한다.

-- CASCADE CONSTRAINTS는 Oracle 전용 문법이라 H2에서 깨진다.
-- FK 의존성 역순(자식 → 부모)으로 드롭해서 양쪽 모두에서 동작하게 한다.
DROP TABLE trip_image;
DROP TABLE trip_record;
DROP TABLE trip;
DROP TABLE party_member;
DROP TABLE party;
DROP TABLE social_account;
DROP TABLE image;
DROP TABLE region;
DROP TABLE service_user;
