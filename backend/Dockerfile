# =============================================================================
# Stage 1: Build — Maven 3.9 + Eclipse Temurin JDK 21
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# --- Dependency cache layer: copy pom.xml + wrapper first ---
COPY pom.xml mvnw ./
COPY .mvn/ .mvn/
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -q

# --- Source compile + package (skip tests in CI; run separately in dev) ---
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B -q

# =============================================================================
# Stage 2: Runtime — Eclipse Temurin JRE 21 (minimal image)
# =============================================================================
FROM eclipse-temurin:21-jre

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
# Database — MySQL 8.0
# Override application-dev.yml hard-coded URL with Docker service name
# DB_USERNAME / DB_PASSWORD are resolved by Spring placeholder ${DB_USERNAME:…}
# =============================================================================
ENV SPRING_DATASOURCE_URL="jdbc:mysql://mysql:3306/cgc_pms?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
ENV DB_USERNAME=cgc
ENV DB_PASSWORD=""

# =============================================================================
# Redis — override localhost with Docker service name
# =============================================================================
ENV SPRING_DATA_REDIS_HOST=redis
ENV SPRING_DATA_REDIS_PORT=6379
ENV SPRING_DATA_REDIS_PASSWORD=""

# =============================================================================
# MinIO object storage
# =============================================================================
ENV MINIO_ENDPOINT=http://minio:9000
ENV MINIO_ACCESS_KEY=minioadmin
ENV MINIO_SECRET_KEY=""

# =============================================================================
# JWT signing secret (MUST be overridden in production)
# =============================================================================
ENV JWT_SECRET=""

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
