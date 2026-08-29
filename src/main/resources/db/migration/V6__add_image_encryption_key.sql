-- V6: 이미지 SSE-C 암호화를 위한 키 저장 컬럼 추가
-- 스토리지에서 SSE-C를 통해 파일을 다운로드/복호화할 때 쓰이는 DEK(Data Encryption Key)를
-- 서버의 KMS(Master Key)로 암호화하여 저장합니다.

ALTER TABLE image ADD encrypted_key VARCHAR2(255);
