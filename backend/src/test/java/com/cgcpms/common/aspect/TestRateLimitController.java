package com.cgcpms.common.aspect;

import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.annotation.RateLimitKey;
import com.cgcpms.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller endpoints for RateLimitAspect test scenarios.
 * Registered by {@code @ComponentScan} in the same package as the test.
 */
@RestController
@RequestMapping("/test-rl")
public class TestRateLimitController {

    @GetMapping("/user-limited")
    @RateLimit(maxRequests = 3, windowSeconds = 60, key = RateLimitKey.USER)
    public ApiResponse<String> userLimited() {
        return ApiResponse.success("ok");
    }

    @GetMapping("/tenant-limited")
    @RateLimit(maxRequests = 3, windowSeconds = 60, key = RateLimitKey.TENANT)
    public ApiResponse<String> tenantLimited() {
        return ApiResponse.success("ok");
    }

    @GetMapping("/ip-limited")
    @RateLimit(maxRequests = 5, windowSeconds = 60, key = RateLimitKey.IP)
    public ApiResponse<String> ipLimited() {
        return ApiResponse.success("ok");
    }

    @GetMapping("/short-window")
    @RateLimit(maxRequests = 3, windowSeconds = 1, key = RateLimitKey.IP)
    public ApiResponse<String> shortWindow() {
        return ApiResponse.success("ok");
    }

    @GetMapping("/ip-account-limited")
    @RateLimit(maxRequests = 4, windowSeconds = 60, key = RateLimitKey.IP_AND_ACCOUNT)
    public ApiResponse<String> ipAccountLimited() {
        return ApiResponse.success("ok");
    }

    @GetMapping("/concurrency-limited")
    @RateLimit(maxRequests = 5, windowSeconds = 60, key = RateLimitKey.IP)
    public ApiResponse<String> concurrencyLimited() {
        return ApiResponse.success("ok");
    }
}
