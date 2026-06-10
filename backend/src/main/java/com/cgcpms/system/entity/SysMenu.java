package com.cgcpms.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long parentId;

    private String menuName;

    /** DIR / MENU / BUTTON */
    private String menuType;

    private String path;

    private String component;

    private String perms;

    private String icon;

    private Integer orderNum;

    private String status;

    private Integer visible;

    @TableField(exist = false)
    private List<SysMenu> children;
}
