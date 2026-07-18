package com.cgcpms.config;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinioConfigTest {

    @Test
    void presignClientUsesConfiguredBrowserReachableEndpoint() throws Exception {
        MinioConfig config = new MinioConfig();
        config.setEndpoint("http://minio:9000");
        config.setPublicEndpoint("http://localhost:9000");
        config.setAccessKey("test-access-key");
        config.setSecretKey("test-secret-key");

        String url = config.presignClient().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket("cgc-pms")
                .object("SETTLEMENT/1/generated/test.pdf")
                .region("us-east-1")
                .expiry(300)
                .build());

        assertTrue(url.startsWith("http://localhost:9000/cgc-pms/SETTLEMENT/1/generated/test.pdf?"), url);
        assertTrue(url.contains("X-Amz-Signature="), url);
    }
}
