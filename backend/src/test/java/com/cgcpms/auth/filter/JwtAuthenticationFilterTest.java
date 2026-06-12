package com.cgcpms.auth.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("JWT authentication filter")
class JwtAuthenticationFilterTest {

    private final ExposedJwtAuthenticationFilter filter = new ExposedJwtAuthenticationFilter();

    @Test
    @DisplayName("does not skip notification stream initial requests")
    void doesNotSkipNotificationStreamInitialRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notifications/stream");
        request.setServletPath("/notifications/stream");

        assertFalse(filter.shouldSkip(request));
    }

    @Test
    @DisplayName("participates in async dispatches")
    void participatesInAsyncDispatches() {
        assertFalse(filter.shouldSkipAsyncDispatch());
    }

    private static class ExposedJwtAuthenticationFilter extends JwtAuthenticationFilter {
        ExposedJwtAuthenticationFilter() {
            super(null, null, null, null, null);
        }

        boolean shouldSkip(HttpServletRequest request) {
            return shouldNotFilter(request);
        }

        boolean shouldSkipAsyncDispatch() {
            return shouldNotFilterAsyncDispatch();
        }
    }
}
