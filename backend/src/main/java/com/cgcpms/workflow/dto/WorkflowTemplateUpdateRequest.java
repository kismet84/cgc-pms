package com.cgcpms.workflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkflowTemplateUpdateRequest {

    @NotBlank(message = "流程名称不能为空")
    private String templateName;

    @Min(value = 0, message = "启用状态只能为0或1")
    @Max(value = 1, message = "启用状态只能为0或1")
    private Integer enabled;

    @DecimalMin(value = "0.00", message = "金额下限不能小于0")
    private BigDecimal amountMin;

    @DecimalMin(value = "0.00", message = "金额上限不能小于0")
    private BigDecimal amountMax;

    private String conditionRule;

    private String formSchema;

    private String remark;
}
