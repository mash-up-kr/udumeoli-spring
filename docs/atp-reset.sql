-- ATP 스키마 리셋 (1회성)
-- 수동 DDL로 만들었던 기존 테이블을 정리해서 Flyway V1이 깨끗하게 적용되도록 한다.
-- 실행 방법: OCI 콘솔 → ATP → Database Actions → SQL Worksheet에 **APP_USER로 접속**해서 실행.
-- (ADMIN으로 실행하면 ADMIN 스키마를 뒤지므로 효과 없음)
--
-- ⚠️ APP_USER 스키마의 해당 테이블과 데이터가 전부 삭제된다. dev 데이터가 필요 없을 때만 실행할 것.

BEGIN
    FOR t IN (
        SELECT table_name
        FROM user_tables
        WHERE table_name IN (
            -- V2에서 단수로 통일 (구 복수형 이름도 함께 정리)
            'TRIP_IMAGE', 'TRIP', 'REGION',
            'TRIP_IMAGES', 'TRIPS', 'IMAGE', 'REGIONS',
            'PARTY_MEMBER', 'PARTY', 'SOCIAL_ACCOUNT', 'SERVICE_USER',
            'FLYWAY_SCHEMA_HISTORY'
        )
    ) LOOP
        EXECUTE IMMEDIATE 'DROP TABLE "' || t.table_name || '" CASCADE CONSTRAINTS PURGE';
    END LOOP;
END;
/

-- 확인: 남은 테이블 조회 (비어 있어야 정상)
SELECT table_name FROM user_tables ORDER BY table_name;
