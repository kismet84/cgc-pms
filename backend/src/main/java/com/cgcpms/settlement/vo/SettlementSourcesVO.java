package com.cgcpms.settlement.vo;

import lombok.Data;

import java.math.BigDecimal;
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
        private BigDecimal confirmedAmount;
        private String approvalStatus;
    }

    @Data
    public static class SubMeasureVO {
        private Long id;
        private String measureCode;
        private String measurePeriod;
        private BigDecimal approvedAmount;
        private String approvalStatus;
    }

    @Data
    public static class PayRecordVO {
        private Long id;
        private BigDecimal payAmount;
        private String payDate;
        private String payMethod;
        private String voucherNo;
        private String payStatus;
    }
}
