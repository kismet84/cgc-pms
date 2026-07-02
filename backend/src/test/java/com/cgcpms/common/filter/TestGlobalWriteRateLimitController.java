package com.cgcpms.common.filter;

import com.cgcpms.common.annotation.RateLimit;
import com.cgcpms.common.annotation.RateLimitKey;
import com.cgcpms.common.result.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test-global-rl")
class TestGlobalWriteRateLimitController {

    @PostMapping("/write")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> write() {
        return ApiResponse.success("ok");
    }

    @GetMapping("/read")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> read() {
        return ApiResponse.success("ok");
    }

    @PostMapping("/strict")
    @PreAuthorize("isAuthenticated()")
    @RateLimit(maxRequests = 1, windowSeconds = 60, key = RateLimitKey.USER)
    public ApiResponse<String> strict() {
        return ApiResponse.success("ok");
    }
}
