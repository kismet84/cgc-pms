package com.cgcpms.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 项目结算金额唯一计算口径。
 *
 * <p>ct_contract.current_amount 已包含正式合同变更（CT_CHANGE）；change_amount
 * 是业主已确认的现场签证（VAR_ORDER），两者是不同业务事实。合同金额只作为履约上限，
 * 不再与已审批产值重复相加。最终结算金额为：已审批计量 + 已确认现场签证 - 终期扣款。</p>
 */
public final class SettlementAmountPolicy {

    public static final String FORMULA_VERSION = "APPROVED_MEASURE_VARIATION_DEDUCTION_V2";
    public static final String LEGACY_UNVERIFIED_VERSION = "LEGACY_UNVERIFIED";
    public static final BigDecimal DEFAULT_WARRANTY_RATE = new BigDecimal("0.05");

    private SettlementAmountPolicy() {
    }

    public static SettlementAmountSnapshot calculate(
            BigDecimal effectiveContractAmount,
            BigDecimal confirmedVariationAmount,
            BigDecimal approvedMeasuredAmount,
            BigDecimal deductionAmount,
            BigDecimal paidAmount) {
        BigDecimal contract = money(effectiveContractAmount);
        BigDecimal variation = money(confirmedVariationAmount);
        BigDecimal measured = money(approvedMeasuredAmount);
        BigDecimal deduction = money(deductionAmount);
        BigDecimal paid = money(paidAmount);

        BigDecimal finalAmount = measured
                .add(variation)
                .subtract(deduction)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal warrantyAmount = finalAmount
                .multiply(DEFAULT_WARRANTY_RATE)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal unpaidAmount = finalAmount
                .subtract(paid)
                .subtract(warrantyAmount)
                .setScale(2, RoundingMode.HALF_UP);

        return new SettlementAmountSnapshot(
                contract,
                variation,
                measured,
                deduction,
                paid,
                finalAmount,
                warrantyAmount,
                unpaidAmount,
                FORMULA_VERSION);
    }

    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
