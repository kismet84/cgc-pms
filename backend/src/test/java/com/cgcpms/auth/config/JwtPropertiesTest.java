package com.cgcpms.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "jwt.expiration=900000",
        "jwt.secret=test-secret-key-minimum-256-bit-length-for-hs256-algorithm",
})
@ActiveProfiles("local")
@DisplayName("JWT 属性配置测试")
class JwtPropertiesTest {

    @Autowired
    private JwtProperties jwtProperties;

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
