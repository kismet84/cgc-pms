package com.cgcpms.overhead.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OverheadAllocationRuleCreateRequest {

    @NotNull(message = "成本科目不能为空")
    private Long costSubjectId;

    @Pattern(regexp = "DIRECT_LABOR|CONTRACT_AMOUNT|USAGE", message = "分摊依据无效")
    private String allocationBasis;

    @Pattern(regexp = "MONTHLY|PER_OCCURRENCE", message = "分摊周期无效")
    private String allocationCycle;
}
