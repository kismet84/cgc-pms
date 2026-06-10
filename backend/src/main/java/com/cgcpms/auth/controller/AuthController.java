package com.cgcpms.auth.controller;

import com.cgcpms.auth.context.UserContext;
import com.cgcpms.auth.dto.LoginRequest;
import com.cgcpms.auth.dto.LoginResponse;
import com.cgcpms.auth.dto.UserInfo;
import com.cgcpms.auth.service.AuthService;
import com.cgcpms.common.result.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/userinfo")
    public ApiResponse<UserInfo> userInfo() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(authService.getUserInfo(userId));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        UserContext.clear();
        return ApiResponse.success();
    }
}
