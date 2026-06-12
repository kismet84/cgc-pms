package com.cgcpms.project.vo;

import lombok.Data;

@Data
public class PmProjectMemberVO {

    private String id;
    private String tenantId;
    private String projectId;
    private String userId;
    private String roleCode;
    private String positionName;
    private String startDate;
    private String endDate;
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
