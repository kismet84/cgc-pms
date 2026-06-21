package com.cgcpms.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator HealthIndicator for MinIO object storage.
 *
 * Checks MinIO connectivity by listing buckets.
 * Returns {@link Health#up()} when MinIO is reachable,
 * {@link Health#down()} when the connection fails or the client is unavailable.
 *
 * Exposed at: GET /api/actuator/health → "minio" component
 * Gracefully degrades when MinIO is disabled (minio.enabled=false).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MinioClient.class)
public class MinioHealthIndicator implements HealthIndicator {

    private final ObjectProvider<MinioClient> minioClientProvider;

    @Override
    public Health health() {
        MinioClient client = minioClientProvider.getIfAvailable();
        if (client == null) {
            return Health.down()
                    .withDetail("reason", "MinioClient bean not available")
                    .build();
        }

        try {
            client.listBuckets();
            return Health.up()
                    .withDetail("endpoint", "reachable")
                    .build();
        } catch (Exception e) {
            log.warn("MinIO health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
}
