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

# Runtime-owned persistent paths. Compose mounts heapdumps separately so OOM
# evidence survives container replacement without landing in the image layer.
RUN mkdir -p /var/log/cgc-pms /var/lib/cgc-pms/heapdumps \
    && chown -R appuser:appgroup /var/log/cgc-pms /var/lib/cgc-pms

# Install curl for HEALTHCHECK
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# ---------------------------------------------------------------------------
# JVM tuning for production
# -XX:InitialRAMPercentage=25.0  initial heap from the cgroup budget
# -XX:MaxRAMPercentage=65.0      reserve at least 35% for non-heap/native memory
# -XX:+HeapDumpOnOutOfMemoryError dump heap on OOM
# -XX:HeapDumpPath=/var/lib/cgc-pms/heapdumps persistent Compose volume
# -XX:+UseContainerSupport       respect cgroup limits (default in 21)
# ---------------------------------------------------------------------------
ENV JAVA_OPTS="-Duser.timezone=Asia/Shanghai -XX:+UseContainerSupport -XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=65.0 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/lib/cgc-pms/heapdumps"

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
# Database — MySQL 8.0. URL and credentials MUST be provided by deployment
# configuration. The image intentionally has no datasource fallback so prod
# validation cannot be bypassed by an unsafe image default.
# =============================================================================
# SPRING_DATASOURCE_URL — injected by deployment configuration
# DB_USERNAME — injected by deployment configuration
# DB_PASSWORD — injected by deployment configuration

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
# MINIO_ACCESS_KEY — injected as a separate least-privilege application account
# MINIO_SECRET_KEY — injected as that application account's independent secret

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
