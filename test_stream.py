import hmac
import hashlib
import base64
import time
import urllib.request
import urllib.error

master_key = b"a3fb1bd23da0ac66e518478bfe8c0c1e"
object_key = "original/b922d8e3-382a-48b6-944d-59e2b608c47d.png"
expires = str(int(time.time()) + 3600*24) # 24시간 뒤 만료

payload = f"{object_key}:{expires}".encode('utf-8')
sig_bytes = hmac.new(master_key, payload, hashlib.sha256).digest()
sig = base64.urlsafe_b64encode(sig_bytes).decode('utf-8').rstrip("=")

url = f"https://pinnnned.duckdns.org/stream/{object_key}?type=original&expires={expires}&sig={sig}"
print("요청 URL:", url)

req = urllib.request.Request(url)
try:
    with urllib.request.urlopen(req) as response:
        print("✅ 성공! HTTP 상태 코드:", response.status) 
        print("바이트 샘플:", response.read(10)) 
except urllib.error.HTTPError as e:
    print("❌ 실패! 상태 코드:", e.code)
    print("에러 내용:", e.read().decode('utf-8', errors='ignore'))
