package com.cgcpms.project.vo;

import lombok.Data;

@Data
public class PmProjectVO {

    private String id;
    private String tenantId;
    private String orgId;
    private String projectCode;
    private String projectName;
    private String projectType;
    private String projectAddress;
    private String ownerUnit;
    private String supervisorUnit;
    private String designUnit;
    private String contractAmount;
    private String targetCost;
    private String plannedStartDate;
    private String plannedEndDate;
    private String actualStartDate;
    private String actualEndDate;
    private String projectManagerId;
    private String status;
    private String approvalStatus;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
