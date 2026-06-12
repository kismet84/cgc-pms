package com.cgcpms.org.vo;

import lombok.Data;

@Data
public class OrgDepartmentVO {

    private String id;
    private String companyId;
    private String parentId;
    private String deptCode;
    private String deptName;
    private Integer orderNum;
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
