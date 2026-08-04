package com.cgcpms.project.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.util.List;

public record ProjectActivationReadinessVO(
        String projectId,
        String initiationBasis,
        String ownerContractId,
        String ownerContractCode,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal ownerContractAmount,
        String costTargetId,
        String budgetId,
        String scheduleId,
        String commencementId,
        String commencementStatus,
        boolean ready,
        List<String> blockers) {
}
