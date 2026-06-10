package com.cgcpms.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration bound from the {@code jwt.*} properties.
 */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HMAC signing secret (must be at least 256 bits / 32 bytes for HS256). */
    private String secret;

    /** Token time-to-live in milliseconds. */
    private long expiration;

    /** HTTP header carrying the token. */
    private String header;

    /** Prefix preceding the token value, e.g. {@code "Bearer "}. */
    private String tokenPrefix;

    /** Refresh token time-to-live in milliseconds (default 7 days). */
    private long refreshExpiration = 604800000L;
}
