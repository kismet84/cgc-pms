package com.cgcpms.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for self-service profile update.
 * Only {@code realName}, {@code phone}, {@code email}, and {@code avatar} are accepted;
 * all other fields (username, roles, status, isAdmin, tenantId, orgId) are ignored server-side.
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 100, message = "真实姓名不能超过100个字符")
    @Pattern(regexp = ".*\\S.*", message = "真实姓名不能为空")
    private String realName;

    @Size(max = 50, message = "手机号不能超过50个字符")
    private String phone;

    @Size(max = 128, message = "邮箱不能超过128个字符")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(max = 500, message = "头像地址不能超过500个字符")
    private String avatar;
}
