package com.cgcpms.system.dict.vo;

import lombok.Data;

@Data
public class SysDictGroupVO {
    private String id;
    private String groupCode;
    private String groupName;
    private Integer orderNum;
    private String status;
    private String createdAt;
    private String updatedAt;
}
