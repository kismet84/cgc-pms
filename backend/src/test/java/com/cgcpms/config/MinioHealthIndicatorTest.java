package com.cgcpms.config;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MinioHealthIndicator - bucket health check")
class MinioHealthIndicatorTest {

    @Test
    @DisplayName("configured bucket exists -> UP")
    void shouldReportUpWhenConfiguredBucketExists() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        MinioConfig config = new MinioConfig();
        config.setBucket("cgc-pms");

        MinioHealthIndicator indicator = new MinioHealthIndicator(provider(client), config);
        Health health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals("cgc-pms", health.getDetails().get("bucket"));
        verify(client).bucketExists(any(BucketExistsArgs.class));
    }

    @Test
    @DisplayName("configured bucket missing -> DOWN")
    void shouldReportDownWhenConfiguredBucketMissing() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        MinioConfig config = new MinioConfig();
        config.setBucket("missing-bucket");

        MinioHealthIndicator indicator = new MinioHealthIndicator(provider(client), config);
        Health health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("missing-bucket", health.getDetails().get("bucket"));
        assertEquals("Configured bucket does not exist", health.getDetails().get("reason"));
    }

    @Test
    @DisplayName("client unavailable -> DOWN")
    void shouldReportDownWhenClientUnavailable() {
        MinioConfig config = new MinioConfig();
        config.setBucket("cgc-pms");

        MinioHealthIndicator indicator = new MinioHealthIndicator(provider(null), config);
        Health health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("MinioClient bean not available", health.getDetails().get("reason"));
        assertFalse(health.getDetails().containsKey("bucket"));
    }

    @Test
    @DisplayName("bucketExists throws exception -> DOWN")
    void shouldReportDownWhenBucketExistsThrowsException() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any(BucketExistsArgs.class))).thenThrow(new RuntimeException("boom"));

        MinioConfig config = new MinioConfig();
        config.setBucket("cgc-pms");

        MinioHealthIndicator indicator = new MinioHealthIndicator(provider(client), config);
        Health health = indicator.health();

        assertEquals("DOWN", health.getStatus().getCode());
        assertEquals("RuntimeException: boom", health.getDetails().get("error"));
    }

    private ObjectProvider<MinioClient> provider(MinioClient client) {
        return new ObjectProvider<>() {
            @Override
            public MinioClient getObject(Object... args) {
                if (client == null) {
                    throw new IllegalStateException("MinioClient bean not available");
                }
                return client;
            }

            @Override
            public MinioClient getIfAvailable() {
                return client;
            }

            @Override
            public MinioClient getIfUnique() {
                return client;
            }

            @Override
            public MinioClient getObject() {
                return getObject(new Object[0]);
            }
        };
    }
}
