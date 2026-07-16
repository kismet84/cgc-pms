package com.cgcpms.settlement.vo;

import lombok.Data;

/**
 * 历史结算金额差异预览。只读，不执行任何历史数据修复。
 */
@Data
public class SettlementAmountBaselineVO {
    private String settlementId;
    private String settlementCode;
    private String projectId;
    private String contractId;
    private String storedFormulaVersion;
    private String targetFormulaVersion;
    private String storedContractAmount;
    private String currentEffectiveContractAmount;
    private String storedChangeAmount;
    private String currentConfirmedVariationAmount;
    private String storedMeasuredAmount;
    private String currentApprovedMeasuredAmount;
    private String deductionAmount;
    private String storedPaidAmount;
    private String currentPaidAmount;
    private String storedFinalAmount;
    private String recalculatedFinalAmount;
    private String finalAmountDelta;
    private String storedWarrantyAmount;
    private String recalculatedWarrantyAmount;
    private String storedUnpaidAmount;
    private String recalculatedUnpaidAmount;
    private boolean amountConsistent;
    private boolean formulaVersionCurrent;
    private String recommendedAction;
}
