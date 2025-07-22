# ---------- 1. Build ----------
FROM gradle:8-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle clean shadowJar --no-daemon

# ---------- 2. Runtime ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# copy the externalised config -->
COPY src/main/resources/application.yaml /app/application.yaml
COPY --from=builder /app/build/libs/*.jar /app/app.jar

EXPOSE 8080
# Railway will set PORT; we forward it to Ktor via -D
ENTRYPOINT ["sh", "-c", "java -jar /app/app.jar -config=/app/application.yaml"]
