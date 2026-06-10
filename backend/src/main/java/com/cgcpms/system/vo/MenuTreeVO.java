package com.cgcpms.system.vo;

import lombok.Data;

import java.util.List;

@Data
public class MenuTreeVO {

    private Long id;
    private Long parentId;
    private String menuName;
    private String menuType;
    private String path;
    private String component;
    private String perms;
    private String icon;
    private Integer orderNum;
    private List<MenuTreeVO> children;
}
