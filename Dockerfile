# ===== Build stage =====
FROM ghcr.io/graalvm/graalvm-community:21 AS builder
WORKDIR /app

# 의존성 캐싱 (gradle 파일 먼저 복사)
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon || true

# 소스 복사 후 jar 빌드 (JIT 벤치마크용 — native 빌드 아님)
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

# ===== Runtime stage =====
FROM ghcr.io/graalvm/graalvm-community:21
WORKDIR /app

# Wallet 마운트 포인트
ENV TNS_ADMIN=/opt/oracle/wallet

# jar만 복사
COPY --from=builder /app/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
