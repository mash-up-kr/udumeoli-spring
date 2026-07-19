-- V3: 이미지 파이프라인 — image.object_key 추가
--   * presigned URL 발급 시점에 스토리지 키를 확정해 저장한다.
--     업로드 검증(HEAD)/삭제가 URL 파싱 없이 키로 바로 동작하게 하기 위함.
--   * 상태 컬럼은 두지 않는다 (V2 결정 유지 — 업로드 여부는 스토리지가 source of truth).
-- Oracle(ATP)과 H2(MODE=Oracle) 양쪽에서 동일하게 동작해야 한다.

ALTER TABLE image ADD object_key VARCHAR2(200);

-- 파이프라인 도입 전 기존 행 백필: 실제 키를 알 수 없으므로 legacy 마커를 채운다.
-- (스토리지에 존재하지 않는 키라 이후 HEAD/삭제 시도는 안전하게 no-op)
UPDATE image SET object_key = 'legacy/' || id WHERE object_key IS NULL;

ALTER TABLE image MODIFY object_key NOT NULL;
ALTER TABLE image ADD CONSTRAINT uq_image_object_key UNIQUE (object_key);
