package com.cgcpms.subcontract.vo;

import lombok.Data;

import java.util.List;

@Data
public class SubMeasureVO {
    private String id;
    private String tenantId;
    private String projectId;
    private String contractId;
    private String partnerId;
    private String subTaskId;
    private String subTaskCode;
    private String subTaskName;
    private String measureCode;
    private String measurePeriod;
    private String measureDate;
    private String reportedAmount;
    private String approvedAmount;
    private String deductionAmount;
    private String netAmount;
    private String approvalStatus;
    private Integer costGeneratedFlag;
    private String status;
    private String projectName;
    private String contractName;
    private String partnerName;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
    private List<SubMeasureItemVO> items;
}
