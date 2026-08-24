-- service_user.profile_image가 NUMBER(2)라 업로드한 image.id(예: 143)가 들어가면
-- ORA-01438(value larger than specified precision)로 회원가입이 실패한다.
-- V1은 이미 배포 DB에 적용되어(Flyway 체크섬 고정) 더 이상 수정할 수 없으므로 새 마이그레이션으로 넓힌다.
ALTER TABLE service_user MODIFY profile_image NUMBER(19);
