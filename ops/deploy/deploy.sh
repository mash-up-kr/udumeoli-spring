#!/bin/bash
# 서버(/home/deploy)에서 실행되는 무중단 배포 스크립트. CI가 SSH로 호출한다.
#
# Usage: deploy.sh <stage_dir> <expected_sha> <expected_archive_hash> [expected_old_image]
#
#   stage_dir             : image.tar.gz / docker-compose.yml / google-token.json을 올려둔 디렉토리
#   expected_sha           : 배포하는 커밋의 git sha (이미지 태그로 쓴다)
#   expected_archive_hash  : image.tar.gz의 sha256 (CI가 계산한 값과 대조)
#   expected_old_image     : (선택) 배포 직전 떠 있어야 할 이미지 sha256. 주어지면
#                            실제 떠 있는 이미지와 다를 때 배포를 막는다(드리프트 감지).
#                            비워두면 이 검증은 건너뛴다.
#
# 실패하면 trap이 이전 이미지/compose로 자동 롤백하고, 헬스체크가 UP이 될 때까지 기다린다.
set -Eeuo pipefail

stage_dir=$1
expected_sha=$2
expected_archive_hash=$3
expected_old_image=${4:-}
app_dir=/home/deploy/app
compose_file="$app_dir/docker-compose.yml"
old_image_tag="udumeoli-backend:rollback-$(date -u +%Y%m%dT%H%M%SZ)"
new_image_tag="udumeoli-backend:$expected_sha"

exec 9>/home/deploy/.udumeoli-image-deploy.lock
flock -n 9
cd "$app_dir"
old_image=$(docker inspect --format '{{.Image}}' udumeoli-app)
old_state=$(docker inspect --format '{{.State.Status}}' udumeoli-app)
test "$old_state" = running
if [ -n "$expected_old_image" ]; then
    test "$old_image" = "$expected_old_image"
fi
actual_hash=$(sha256sum "$stage_dir/image.tar.gz" | awk '{print $1}')
test "$expected_archive_hash" = "$actual_hash"
docker compose --project-directory "$app_dir" -f "$stage_dir/docker-compose.yml" config --quiet
old_compose=$(mktemp "$app_dir/.docker-compose.rollback.XXXXXX")
cp -p "$compose_file" "$old_compose"
docker tag "$old_image" "$old_image_tag"
replacement_started=0
rollback() {
    status=$?
    trap - EXIT
    if [ "$status" -ne 0 ]; then
        echo "Deployment failed, restoring $old_image_tag" >&2
        docker tag "$old_image_tag" udumeoli-backend:latest || true
        if [ "$replacement_started" -eq 1 ]; then
            cp -p "$old_compose" "$compose_file" || true
            docker compose up -d --pull never app || true
            for _ in $(seq 1 25); do
                if curl -fsS --max-time 2 http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
                    echo 'Previous image health UP'
                    break
                fi
                sleep 2
            done
        fi
    fi
    exit "$status"
}
trap rollback EXIT

docker image load --input "$stage_dir/image.tar.gz"
new_image=$(docker image inspect --format '{{.Id}}' udumeoli-backend:latest)
test "$new_image" != "$old_image"
test "$(docker image inspect --format '{{.Os}}/{{.Architecture}}' udumeoli-backend:latest)" = linux/amd64
docker tag "$new_image" "$new_image_tag"
replacement_started=1
pending_compose=$(mktemp "$app_dir/.docker-compose.deploy.XXXXXX")
cat "$stage_dir/docker-compose.yml" > "$pending_compose"
chmod --reference="$compose_file" "$pending_compose"
mv -f "$pending_compose" "$compose_file"
docker compose up -d --pull never app
test "$(docker inspect --format '{{.Image}}' udumeoli-app)" = "$new_image"

healthy=0
for _ in $(seq 1 30); do
    if curl -fsS --max-time 2 http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
        healthy=1
        break
    fi
    sleep 2
done
if [ "$healthy" -ne 1 ]; then
    docker inspect --format '{{.State.Status}} {{.State.ExitCode}} {{.RestartCount}}' udumeoli-app >&2
    exit 1
fi

# 실제 구글 계정 없이도, 서버가 OAuth2 자격증명을 쓸 수 있는 상태인지 끝까지 확인한다.
# 일부러 만료/위조된 ID 토큰을 넣어서 "검증 로직까지는 정상 작동 → 그래서 INVALID_TOKEN으로 거절됨"을 본다.
status=$(curl -sS --max-time 10 -o "$stage_dir/google-smoke.json" -w '%{http_code}' \
    -H 'Content-Type: application/json' --data-binary "@$stage_dir/google-token.json" \
    http://127.0.0.1:8080/api/auth/login/google)
echo "Google login smoke HTTP $status"
test "$status" = 401
grep -q '"INVALID_TOKEN"' "$stage_dir/google-smoke.json"

printf 'Running image %s (%s)\nRollback image %s\nCompose backup %s\n' "$new_image" "$expected_sha" "$old_image_tag" "$old_compose"
