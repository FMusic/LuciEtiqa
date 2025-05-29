# 1. build
FROM gradle:8-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle clean shadowJar --no-daemon

# 2. runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
EXPOSE 8080
COPY --from=builder /app/build/libs/*.jar app.jar
CMD ["java","-jar","app.jar","-config","/app/application.yaml"]
