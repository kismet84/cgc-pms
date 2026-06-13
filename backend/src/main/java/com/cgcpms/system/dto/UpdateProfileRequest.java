package com.cgcpms.system.dto;

import lombok.Data;

/**
 * Request DTO for self-service profile update.
 * Only {@code realName}, {@code phone}, {@code email}, and {@code avatar} are accepted;
 * all other fields (username, roles, status, isAdmin, tenantId, orgId) are ignored server-side.
 */
@Data
public class UpdateProfileRequest {

    private String realName;
    private String phone;
    private String email;
    private String avatar;
}
