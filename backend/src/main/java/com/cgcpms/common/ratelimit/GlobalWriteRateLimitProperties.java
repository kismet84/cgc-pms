package com.cgcpms.common.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global rate limit for write APIs.
 */
@Data
@ConfigurationProperties(prefix = "rate-limit.global-write")
public class GlobalWriteRateLimitProperties {

    /**
     * Whether the global write limit is enabled.
     */
    private boolean enabled = true;

    /**
     * Max write requests per window for one user or IP bucket.
     */
    private int maxRequests = 120;

    /**
     * Window size in seconds.
     */
    private int windowSeconds = 60;
}
