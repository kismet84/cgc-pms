package com.cgcpms.system.dict.vo;

import lombok.Data;

@Data
public class SysDictTypeVO {

    private String id;
    private String groupId;
    private String groupCode;
    private String groupName;
    private String dictCode;
    private String dictName;
    private String dictClass;
    private String status;
    private String createdAt;
    private String updatedAt;
}
