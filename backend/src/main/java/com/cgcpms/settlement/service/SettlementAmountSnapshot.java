package com.cgcpms.settlement.service;

import java.math.BigDecimal;

/**
 * 结算金额一次计算产生的不可变快照。
 */
public record SettlementAmountSnapshot(
        BigDecimal effectiveContractAmount,
        BigDecimal confirmedVariationAmount,
        BigDecimal approvedMeasuredAmount,
        BigDecimal deductionAmount,
        BigDecimal paidAmount,
        BigDecimal finalAmount,
        BigDecimal warrantyAmount,
        BigDecimal unpaidAmount,
        String formulaVersion) {
}
