package com.cgcpms.org.vo;

import lombok.Data;

@Data
public class OrgPositionVO {

    private String id;
    private String positionCode;
    private String positionName;
    private String status;
    private String createdBy;
    private String createdAt;
    private String updatedAt;
    private String remark;
}
