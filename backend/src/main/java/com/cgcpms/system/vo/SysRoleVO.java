package com.cgcpms.system.vo;

import lombok.Data;

import java.util.List;

@Data
public class SysRoleVO {

    private Long id;
    private String roleCode;
    private String roleName;
    private String roleType;
    private String status;
    private String dataScope;
    private Long userCount;
    private List<Long> menuIds;
    private String createdAt;
}
