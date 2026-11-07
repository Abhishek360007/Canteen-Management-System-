# One image: the React app is built and bundled into the Spring Boot jar.

FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /api
COPY backend/pom.xml ./
COPY backend/src/main ./src/main
COPY --from=web /web/dist ./src/main/resources/static
# Tests run in CI/locally (they need an embedded PostgreSQL); the image build skips them.
RUN mvn -q -B package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=api /api/target/tulas-canteen-*.jar app.jar
USER app
EXPOSE 8080
# Tuned to fit small hosts (e.g. Render's free 512 MB instance).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+UseSerialGC", "-Xss512k", "-XX:TieredStopAtLevel=1", "-jar", "app.jar"]
