package com.cgcpms.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for self-service password change.
 * Requires old password verification before encoding the new password with BCrypt.
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, message = "新密码长度不能少于8位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新密码至少8位，且必须包含字母和数字")
    private String newPassword;
}
