# ===== Build stage =====
FROM ghcr.io/graalvm/graalvm-community:21 AS builder
WORKDIR /app

# 의존성 캐싱 (gradle 파일 먼저 복사)
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || true

# 소스 복사 후 native 빌드
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew nativeCompile --no-daemon

# ===== Runtime stage =====
FROM debian:bookworm-slim
WORKDIR /app

# 필수 런타임 라이브러리만
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

# Wallet 마운트 포인트
ENV TNS_ADMIN=/opt/oracle/wallet

# native 바이너리만 복사
COPY --from=builder /app/build/native/nativeCompile/udumeoli /app/udumeoli
RUN chmod +x /app/udumeoli

EXPOSE 8080
ENTRYPOINT ["/app/udumeoli"]