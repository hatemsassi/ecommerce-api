# syntax=docker/dockerfile:1

# ---- Build stage: compile and package with the Temurin 21 JDK ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (./mvnw test), not in the image build
RUN ./mvnw -B -q package -DskipTests \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Runtime stage: Temurin 21 JRE only ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /application

RUN addgroup -S -g 1001 spring && adduser -S -u 1001 -G spring spring

# Least-frequently changing layers first for better cache reuse
COPY --from=build --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /workspace/extracted/application/ ./

USER 1001:1001

EXPOSE 8080

# Busybox wget ships with Alpine, so no extra packages are needed
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "application.jar"]
