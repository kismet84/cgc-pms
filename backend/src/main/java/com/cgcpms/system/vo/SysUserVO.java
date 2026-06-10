package com.cgcpms.system.vo;

import lombok.Data;

import java.util.List;

@Data
public class SysUserVO {

    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private String avatar;
    private String status;
    private Integer isAdmin;
    private List<String> roleNames;
    private String createdAt;
    private String updatedAt;
}
