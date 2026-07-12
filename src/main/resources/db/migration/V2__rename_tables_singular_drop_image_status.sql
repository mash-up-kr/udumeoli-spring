-- V2: 스키마 리뷰 반영
--   * 테이블명 단수/복수 혼재 → 단수로 통일 (regions/trips/trip_images → region/trip/trip_image)
--   * image.status 제거 — 업로드 여부는 createTrip 시점에 스토리지를 직접 확인해서 검증하므로
--     DB에 처리 상태 컬럼을 두지 않는다 (GraphQL에도 원래 노출하지 않던 값)
-- V1 주석의 "지역 데이터 V2 시딩"은 이 변경으로 V3 이후로 밀린다.
-- Oracle(ATP)과 H2(MODE=Oracle) 양쪽에서 동일하게 동작해야 한다.

-- 1) image.status 제거
ALTER TABLE image DROP CONSTRAINT ck_image_status;
ALTER TABLE image DROP COLUMN status;

-- 2) 테이블명 단수 통일
ALTER TABLE regions RENAME TO region;
ALTER TABLE trips RENAME TO trip;
ALTER TABLE trip_images RENAME TO trip_image;

-- 3) 제약/인덱스 이름도 새 테이블명에 맞춰 정리
--    (uq_trip_images의 Oracle 백킹 인덱스명은 자동으로 바뀌지 않지만 동작에 영향 없어 그대로 둔다)
ALTER TABLE trip RENAME CONSTRAINT fk_trips_party TO fk_trip_party;
ALTER TABLE trip RENAME CONSTRAINT fk_trips_region TO fk_trip_region;
ALTER TABLE trip RENAME CONSTRAINT fk_trips_creator TO fk_trip_creator;
ALTER TABLE trip_image RENAME CONSTRAINT uq_trip_images TO uq_trip_image;
ALTER TABLE trip_image RENAME CONSTRAINT fk_trip_images_trip TO fk_trip_image_trip;
ALTER TABLE trip_image RENAME CONSTRAINT fk_trip_images_image TO fk_trip_image_image;

ALTER INDEX idx_trips_party RENAME TO idx_trip_party;
ALTER INDEX idx_trips_region RENAME TO idx_trip_region;
ALTER INDEX idx_trips_creator RENAME TO idx_trip_creator;
ALTER INDEX idx_trip_images_image RENAME TO idx_trip_image_image;
