# =============================================================================
# Stage 1: Build — Maven 3.9 + Eclipse Temurin JDK 21
# =============================================================================
FROM maven:3.9-eclipse-temurin-21@sha256:d7e7f57407437c014571f1ad5a9955f03fc3edcb1d964067ef351fa38e798665 AS builder

WORKDIR /build

# --- Dependency cache layer: copy pom.xml + wrapper first ---
COPY pom.xml mvnw ./
COPY .mvn/ .mvn/
RUN chmod +x mvnw && MAVEN_CONFIG="" ./mvnw -C dependency:go-offline -DskipTests -B

# --- Source compile + package (skip tests in CI; run separately in dev) ---
COPY src/ src/
RUN MAVEN_CONFIG="" ./mvnw -C clean package -DskipTests -B

# =============================================================================
# Stage 2: Runtime — Eclipse Temurin JRE 21 (minimal image)
# =============================================================================
FROM eclipse-temurin:21-jre@sha256:194b590649efee5555de080c634905a30c2885190210e37ada26370ed2a9c781

# Non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup -d /app appuser

# Log directory for file-based logging (logback-spring.xml)
RUN mkdir -p /var/log/cgc-pms && chown -R appuser:appgroup /var/log/cgc-pms

# Install curl for HEALTHCHECK
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# ---------------------------------------------------------------------------
# JVM tuning for production
# -Xms512m                       initial heap 512 MB
# -Xmx1g                         max heap 1 GB
# -XX:+HeapDumpOnOutOfMemoryError dump heap on OOM
# -XX:HeapDumpPath=/tmp          heap dump location
# -XX:+UseContainerSupport       respect cgroup limits (default in 21)
# -XX:MaxRAMPercentage=75.0      adaptive heap in containers
# ---------------------------------------------------------------------------
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# ---------------------------------------------------------------------------
# Spring profile (override via docker-compose or k8s for prod)
# ---------------------------------------------------------------------------
ENV SPRING_PROFILES_ACTIVE=prod

# =============================================================================
# NOTE: The following ENV defaults mirror docker-compose.prod.yml settings.
# docker-compose values take precedence at runtime — these are fallback defaults
# for non-Docker deployments. Keep both in sync when changing infrastructure.
# =============================================================================

# =============================================================================
# Database — MySQL 8.0 (Docker service name 'mysql' on internal network)
# All secrets (DB_PASSWORD, etc.) MUST be provided via docker-compose environment.
# Defaults below are intentionally absent — missing vars will cause startup failure.
# =============================================================================
ENV SPRING_DATASOURCE_URL="jdbc:mysql://mysql:3306/cgc_pms?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=true&requireSSL=true&allowPublicKeyRetrieval=true"
# DB_USERNAME — injected by docker-compose from MYSQL_USER
# DB_PASSWORD — injected by docker-compose from MYSQL_PASSWORD

# =============================================================================
# Redis — override localhost with Docker service name
# =============================================================================
ENV SPRING_DATA_REDIS_HOST=redis
ENV SPRING_DATA_REDIS_PORT=6379
# SPRING_DATA_REDIS_PASSWORD — injected by docker-compose from REDIS_PASSWORD

# =============================================================================
# MinIO object storage
# =============================================================================
ENV MINIO_ENDPOINT=http://minio:9000
# MINIO_ACCESS_KEY — injected by docker-compose from MINIO_ROOT_USER
# MINIO_SECRET_KEY — injected by docker-compose from MINIO_ROOT_PASSWORD

# =============================================================================
# JWT signing secret (MUST be injected by docker-compose)
# =============================================================================
# JWT_SECRET — injected by docker-compose from JWT_SECRET

# ---------------------------------------------------------------------------
# Copy the Spring Boot fat jar from the builder stage
# ---------------------------------------------------------------------------
COPY --from=builder /build/target/cgc-pms-backend.jar app.jar

# Switch to non-root user
USER appuser

# ---------------------------------------------------------------------------
# Expose backend port
# ---------------------------------------------------------------------------
EXPOSE 8080

# ---------------------------------------------------------------------------
# Health check — actuator must be enabled in application-{profile}.yml
# ---------------------------------------------------------------------------
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/actuator/health || exit 1

# ---------------------------------------------------------------------------
# Start the application
# ---------------------------------------------------------------------------
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
