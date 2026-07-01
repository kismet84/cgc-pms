package com.cgcpms.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role_menu_audit_snapshot")
public class SysRoleMenuAuditSnapshot {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long operatorId;

    private Long roleId;

    private String beforeMenuIds;

    private String afterMenuIds;

    private Integer successFlag;

    private String errorSummary;

    private LocalDateTime createdAt;
}
