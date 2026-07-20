package com.cgcpms.dashboard.vo;

import lombok.Data;

/**
 * Shared payment item for finance dashboard view.
 */
@Data
public class DashboardPaymentItemVO {
    private String payRecordId;
    private String recordCode;
    private String contractId;
    private String contractName;
    private String partnerName;
    private String payAmount;
    private String payDate;
    private String payStatus;
    private String projectId;
    private String projectName;
}
