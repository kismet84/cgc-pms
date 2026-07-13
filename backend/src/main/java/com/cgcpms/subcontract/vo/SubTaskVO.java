package com.cgcpms.subcontract.vo;

import lombok.Data;

@Data
public class SubTaskVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String contractId;
    private String partnerId;
    private String predecessorTaskId;
    private String predecessorTaskName;
    private String predecessorStatus;
    private String predecessorPlannedEndDate;
    private String predecessorActualEndDate;
    private String taskCode;
    private String taskName;
    private String workArea;
    private String plannedStartDate;
    private String plannedEndDate;
    private String actualStartDate;
    private String actualEndDate;
    private String progressPercent;
    private String status;
    private String projectName;
    private String contractName;
    private String partnerName;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
