package com.cgcpms.dashboard.vo;

import lombok.Data;

/**
 * Shared contract item for dashboard views.
 */
@Data
public class DashboardContractItemVO {
    private String contractId;
    private String contractCode;
    private String contractName;
    private String contractType;
    private String contractAmount;
    private String currentAmount;
    private String paidAmount;
    private String endDate;
    private String projectId;
    private String projectName;
    private String contractStatus;
}
