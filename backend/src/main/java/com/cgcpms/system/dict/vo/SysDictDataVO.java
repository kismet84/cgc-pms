package com.cgcpms.system.dict.vo;

import lombok.Data;

@Data
public class SysDictDataVO {

    private String id;
    private String dictTypeId;
    private String dictLabel;
    private String dictValue;
    private String cssClass;
    private String listClass;
    private Integer orderNum;
    private String status;
    private String createdAt;
    private String updatedAt;
}
