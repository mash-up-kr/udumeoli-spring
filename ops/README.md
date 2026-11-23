# ops — 배포 스크립트

develop 브랜치 배포 서버(`dev-pinnned.duckdns.org`, 168.107.16.45)에 2026-11-13, main의 `ad17106`
(디스코드 로그 전송 기능) 커밋을 미리 수동 테스트하면서 만들어졌던 `deploy.sh`/`google-token.py`를
저장소로 가져와 CI/CD에서 재사용할 수 있게 일반화했다. (같은 서버에 있던 `health_monitor.py`/systemd
타이머는 CI/CD와 무관한 별개 기능이라 저장소에 넣지 않았다 — 서버에서 독립적으로 계속 운영된다.)

## deploy/ — CI가 호출하는 무중단 배포

[.github/workflows/deploy-dev.yml](../.github/workflows/deploy-dev.yml)이 develop 브랜치 push마다
실행한다.

`deploy.sh`는 **이미 `udumeoli-app` 컨테이너가 떠 있다는 걸 전제**로 한다(교체 대상의 "이전 이미지"를
먼저 읽어야 하기 때문). 완전히 새 서버에 처음 올릴 때는 `docker compose up -d`로 최초 1회 수동 기동한
뒤부터 이 파이프라인을 쓸 수 있다.

1. GitHub Actions 러너에서 이미지를 빌드하고(`docker build`), `image.tar.gz`로 저장
2. `google-token.py`로 일부러 무효한 구글 ID 토큰(`google-token.json`)을 생성
3. 서버에 `image.tar.gz` / `docker-compose.yml` / `google-token.json` / `deploy.sh`를
   `/home/deploy/udumeoli-release-<sha>.<random>/`에 올림
4. SSH로 그 디렉토리의 `deploy.sh`를 실행

`deploy.sh`가 하는 일(자세한 건 스크립트 주석 참고):
- `flock`으로 동시 배포 방지
- 전송된 이미지 아카이브의 sha256을 CI가 계산한 값과 대조(전송 무결성)
- (선택) 배포 직전 떠 있던 이미지가 CI가 예상한 것과 같은지 확인 — 다르면 중단(드리프트 감지)
- 이미지 교체 → `/actuator/health`가 UP이 될 때까지 폴링 → 구글 로그인 스모크 테스트(401 INVALID_TOKEN 확인)
- 위 과정 중 어디서든 실패하면 **이전 이미지/compose로 자동 롤백**하고 롤백 후 헬스체크까지 기다림

`main`용 [deploy.yml](../.github/workflows/deploy.yml)은 `docker compose down && up`만 하는 단순한
버전이라 롤백/스모크테스트가 없다. develop 쪽만 이 방식을 쓰는 이유는 CI/CD를 새로 구축하면서
서버에 이미 검증되어 있던 더 안전한 방식을 그대로 썼기 때문 — 추후 main도 이 방식으로 옮기는 걸 고려해볼 만하다.
