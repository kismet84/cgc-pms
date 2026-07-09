package com.cgcpms.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AlertExportAuditRequest {
    @NotBlank(message = "筛选签名不能为空")
    @Size(max = 32, message = "筛选签名长度不能超过32个字符")
    @Pattern(regexp = "^alert-export-[a-f0-9]{1,19}$", message = "筛选签名格式不合法")
    private String filterSignature;

    @NotNull(message = "导出记录数不能为空")
    @PositiveOrZero(message = "导出记录数不能小于0")
    private Integer recordCount;
}
