package com.cgcpms.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** 付款申请草稿更新白名单；sources 非空时与申请头在同一事务保存。 */
public record PayApplicationUpdateRequest(
        Long projectId,
        @NotNull Long contractId,
        Long partnerId,
        Long costSubjectId,
        Long budgetLineId,
        String expenseCategory,
        @NotNull @Positive BigDecimal applyAmount,
        String payType,
        String applyReason,
        String remark,
        @NotNull @Min(0) Integer expectedVersion,
        @Valid @Size(max = 200, message = "付款来源不能超过200条") List<SourceInput> sources) {

    public record SourceInput(
            @NotBlank(message = "付款来源类型不能为空") String sourceType,
            @NotNull(message = "付款来源不能为空") Long sourceRefId,
            @NotNull(message = "付款来源金额不能为空") @Positive(message = "付款来源金额必须大于0")
            BigDecimal sourceAmount) {
    }
}
