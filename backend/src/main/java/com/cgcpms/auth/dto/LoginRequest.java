package com.cgcpms.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class LoginRequest {

    @NotNull(message = "租户ID不能为空")
    @PositiveOrZero(message = "租户ID必须为非负整数")
    private Long tenantId;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
