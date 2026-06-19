package com.cgcpms.cost.vo;

import lombok.Data;

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
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
