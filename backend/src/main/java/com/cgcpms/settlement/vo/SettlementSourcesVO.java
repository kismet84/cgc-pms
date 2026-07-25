package com.cgcpms.settlement.vo;

import lombok.Data;

import java.util.List;

@Data
public class SettlementSourcesVO {
    private List<VarOrderVO> varOrders;
    private List<SubMeasureVO> subMeasures;
    private List<PayRecordVO> payRecords;

    @Data
    public static class VarOrderVO {
        private Long id;
        private String varCode;
        private String varName;
        private String varType;
        private String confirmedAmount;
        private String approvalStatus;
    }

    @Data
    public static class SubMeasureVO {
        private Long id;
        private String measureCode;
        private String measurePeriod;
        private String approvedAmount;
        private String approvalStatus;
    }

    @Data
    public static class PayRecordVO {
        private Long id;
        private String payAmount;
        private String payDate;
        private String payMethod;
        private String voucherNo;
        private String payStatus;
    }
}
