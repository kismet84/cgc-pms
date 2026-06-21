package com.cgcpms.revenue.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 收入确认更新请求 DTO。
 * 白名单控制仅允许编辑业务字段，防止篡改 tenantId、approvalStatus、costItemId。
 */
@Data
public class ContractRevenueUpdateRequest {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate revenueDate;

    private BigDecimal progressPercent;

    private String progressDesc;

    private BigDecimal revenueAmount;

    private BigDecimal revenueTax;

    private BigDecimal billedAmount;

    private BigDecimal billedTax;
}
