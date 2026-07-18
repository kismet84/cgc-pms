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
    /**
     * Browser-reachable endpoint used only when signing download URLs. It may
     * differ from the Docker-internal endpoint used for object I/O.
     */
    private String publicEndpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private String region = "us-east-1";
    private volatile MinioClient presignClient;

    @Bean
    public MinioClient minioClient() {
        return buildClient(endpoint);
    }

    public boolean hasPublicEndpoint() {
        return publicEndpoint != null && !publicEndpoint.isBlank();
    }

    /**
     * The endpoint belongs to the SigV4 canonical request. Generate presigned
     * URLs with this client instead of rewriting an already signed URL.
     */
    public MinioClient presignClient() {
        if (!hasPublicEndpoint()) {
            throw new IllegalStateException("MINIO_PUBLIC_ENDPOINT 未配置");
        }
        MinioClient current = presignClient;
        if (current != null) return current;
        synchronized (this) {
            if (presignClient == null) {
                presignClient = buildClient(publicEndpoint);
            }
            return presignClient;
        }
    }

    private MinioClient buildClient(String targetEndpoint) {
        return MinioClient.builder()
                .endpoint(targetEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
