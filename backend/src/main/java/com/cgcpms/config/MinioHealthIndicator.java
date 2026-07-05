package com.cgcpms.config;

import io.minio.BucketExistsArgs;
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
 * Checks MinIO connectivity by verifying the configured bucket exists.
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
    private final MinioConfig minioConfig;

    @Override
    public Health health() {
        MinioClient client = minioClientProvider.getIfAvailable();
        if (client == null) {
            return Health.down()
                    .withDetail("reason", "MinioClient bean not available")
                    .build();
        }

        try {
            String bucket = minioConfig.getBucket();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                return Health.down()
                        .withDetail("reason", "Configured bucket does not exist")
                        .build();
            }
            return Health.up().build();
        } catch (Exception e) {
            log.warn("MinIO health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", "MinIO connection failed")
                    .build();
        }
    }
}
