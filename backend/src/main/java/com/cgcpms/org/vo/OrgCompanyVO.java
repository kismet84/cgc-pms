package com.cgcpms.org.vo;

import lombok.Data;

@Data
public class OrgCompanyVO {

    private String id;
    private String companyCode;
    private String companyName;
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
