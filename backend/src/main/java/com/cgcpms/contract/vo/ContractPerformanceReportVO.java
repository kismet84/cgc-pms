package com.cgcpms.contract.vo;

import lombok.Data;

import java.util.List;

@Data
public class ContractPerformanceReportVO {
    private String totalContractAmount;
    private String totalChangeAmount;
    private String totalPaidAmount;
    private String paymentProgress;
    private List<Row> rows;

    @Data
    public static class Row {
        private String contractId;
        private String contractCode;
        private String contractName;
        private String contractStatus;
        private String contractAmount;
        private String changeAmount;
        private String paidAmount;
        private String paymentProgress;
    }
}
