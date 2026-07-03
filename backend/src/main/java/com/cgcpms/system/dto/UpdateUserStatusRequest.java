package com.cgcpms.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateUserStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "^(ENABLE|DISABLE)$", message = "状态只能为 ENABLE 或 DISABLE")
    private String status;
}
