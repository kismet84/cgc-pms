package com.cgcpms.overhead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OverheadAllocationRuleUpdateRequest {

    @NotNull(message = "成本科目不能为空")
    private Long costSubjectId;

    @NotBlank(message = "分摊依据不能为空")
    @Pattern(regexp = "DIRECT_LABOR|CONTRACT_AMOUNT|USAGE", message = "分摊依据无效")
    private String allocationBasis;

    @NotBlank(message = "分摊周期不能为空")
    @Pattern(regexp = "MONTHLY|PER_OCCURRENCE", message = "分摊周期无效")
    private String allocationCycle;
}
