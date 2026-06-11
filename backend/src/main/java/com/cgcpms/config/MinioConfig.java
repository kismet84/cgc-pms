package com.cgcpms.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO object storage configuration.
 * Reads minio.* properties from application YAML.
 * Disabled when minio.enabled=false (e.g. in tests without MinIO server).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true", matchIfMissing = true)
public class MinioConfig {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
