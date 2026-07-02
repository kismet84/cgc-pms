package com.cgcpms.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.SecureRandom;
import java.util.Base64;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("JWT 属性配置测试")
class JwtPropertiesTest {

    private static final String JWT_SECRET = generateJwtSecret();

    @Autowired
    private JwtProperties jwtProperties;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("jwt.expiration", () -> 900000);
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    private static String generateJwtSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("access token TTL 应为 15 分钟 (900000ms)")
    void accessTokenExpirationShouldBe15Minutes() {
        assertEquals(900000L, jwtProperties.getExpiration(),
                "Access token TTL must be 900000ms (15 minutes)");
    }

    @Test
    @DisplayName("refresh token TTL 应为 7 天 (604800000ms)")
    void refreshTokenExpirationShouldBe7Days() {
        assertEquals(604800000L, jwtProperties.getRefreshExpiration(),
                "Refresh token TTL must remain 604800000ms (7 days)");
    }
}
