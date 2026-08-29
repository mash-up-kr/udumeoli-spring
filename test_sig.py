import hmac
import hashlib
import base64
import time
import urllib.request

# 설정
master_key = b"a3fb1bd23da0ac66e518478bfe8c0c1e"
image_id = "82"
expires = str(int(time.time()) + 3600)

# 서명 생성
payload = f"{image_id}:{expires}".encode('utf-8')
sig_bytes = hmac.new(master_key, payload, hashlib.sha256).digest()
sig = base64.urlsafe_b64encode(sig_bytes).decode('utf-8').rstrip("=")

url = f"https://pinnnned.duckdns.org/stream/{image_id}?expires={expires}&sig={sig}"
print("요청 URL:", url)

# 호출 (GET 요청으로 변경)
req = urllib.request.Request(url)
try:
    with urllib.request.urlopen(req) as response:
        print("✅ 성공! 상태 코드:", response.status) 
        print("응답 헤더:", response.headers)
        # 이미지 내용이 스트리밍됩니다 (터미널이 깨질 수 있으니 10바이트만 확인)
        print("바이트 샘플:", response.read(10)) 
except urllib.error.HTTPError as e:
    # 404 (DB에 1번 이미지가 없음)가 뜨면 서명 검증은 성공한 것입니다!
    # 401 이 뜨면 서명(비밀키) 불일치입니다.
    print("❌ 에러 발생:", e.code, e.reason)
    print("에러 내용:", e.read().decode('utf-8', errors='ignore'))
