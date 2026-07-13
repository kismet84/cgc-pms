package com.cgcpms.common.filter;

import com.cgcpms.common.ratelimit.GlobalWriteRateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("local")
class GlobalWriteRateLimitTestDefaultsTest {

    @Autowired
    private GlobalWriteRateLimitProperties properties;

    @Test
    void globalWriteRateLimitIsDisabledForUnrelatedIntegrationTests() {
        assertFalse(properties.isEnabled());
    }
}
