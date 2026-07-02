package com.cgcpms.system.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class SysMenuVO implements Serializable {

    private Long id;
    private Long parentId;
    private String menuName;
    private String menuType;
    private String path;
    private String component;
    private String perms;
    private String icon;
    private Integer orderNum;
    private String status;
    private Integer visible;
}
