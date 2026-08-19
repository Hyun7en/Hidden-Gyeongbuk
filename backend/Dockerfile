# 빌드 컨텍스트는 저장소 루트다(docker-compose.yml의 context: .).
# backend/ 를 컨텍스트로 두면 프론트 소스가 보이지 않아 번들을 함께 넣을 수 없다 —
# 아키텍처 §8이 "정적 번들은 백엔드에서 서빙한다"로 확정했으므로 둘이 한 이미지에 들어간다.

# ── 1단계: React 번들 ──────────────────────────────────────────────────────────
FROM node:22-bookworm-slim AS frontend
WORKDIR /frontend
# 의존성 설치를 먼저 캐싱한다 — 소스만 바뀌면 이 레이어를 재사용한다.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# VITE_* 값은 빌드 시점에 번들로 구워진다(런타임 환경변수가 아니다).
# VITE_API_BASE_URL은 비워 둔다 — 같은 오리진에서 API가 서빙되므로 상대 경로가 맞다.
ARG VITE_KAKAO_MAP_KEY=""
ENV VITE_KAKAO_MAP_KEY=${VITE_KAKAO_MAP_KEY}
RUN npm run build

# ── 2단계: Spring Boot jar ────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY backend/gradlew backend/settings.gradle backend/build.gradle ./
COPY backend/gradle ./gradle
RUN ./gradlew dependencies --no-daemon || true
COPY backend/src ./src
# build.gradle이 ../frontend/dist에서 번들을 가져가므로 1단계 산출물을 그 자리에 놓는다.
COPY --from=frontend /frontend/dist /frontend/dist
# npm은 이 스테이지에 없다 — 번들은 위에서 이미 만들었으므로 복사만 시킨다.
RUN ./gradlew bootJar --no-daemon -PskipFrontendBuild

# ── 3단계: 런타임 ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
