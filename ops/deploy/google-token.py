# deploy.sh의 배포 후 스모크 테스트용. 서명이 안 된, 일부러 무효한 구글 ID 토큰을 하나 만든다.
#
#   python3 google-token.py > google-token.json
#
# 이 토큰으로 /api/auth/login/google을 호출하면 "서명 검증 로직까지는 정상 작동해서
# 401 INVALID_TOKEN으로 거절되는지"를 확인할 수 있다 — 실제 구글 계정 없이도 OAuth2 설정
# (클라이언트 ID 등)이 배포 후에도 깨지지 않았는지 보는 용도다.
import base64
import json
import time
import urllib.error
import urllib.request

def encode(value):
    return base64.urlsafe_b64encode(value).decode().rstrip('=')

now = int(time.time())
header = {'alg': 'RS256', 'kid': 'deploy-smoke-test-key'}
claims = {'iss': 'https://accounts.google.com', 'sub': 'deploy-smoke-test-no-account',
          'aud': 'deploy-smoke-test-invalid-client', 'iat': now - 7200, 'exp': now - 3600}
token = '.'.join([encode(json.dumps(value).encode()) for value in (header, claims)] + [encode(bytes([1]) * 256)])
print(json.dumps({'idToken': token}))
