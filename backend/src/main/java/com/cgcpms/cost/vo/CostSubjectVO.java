package com.cgcpms.cost.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CostSubjectVO {
    private String id;
    private String tenantId;
    private String parentId;
    private String subjectCode;
    private String subjectName;
    private String subjectType;
    private String accountCategory;
    private Integer level;
    private Integer sortOrder;
    private String status;
    private BigDecimal defaultTargetRatio;
    private Integer ledgerFlag;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
