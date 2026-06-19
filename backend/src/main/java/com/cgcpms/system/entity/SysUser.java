package com.cgcpms.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @NotBlank
    @Size(max = 50)
    private String username;

    private String password;

    @Size(max = 50)
    private String realName;

    private String phone;

    private String email;

    /** @see V34__add_project_member_and_user_org.sql — org_id added to sys_user */
    private Long orgId;

    private String avatar;

    private String status;

    private Integer isAdmin;

    /** 接收前端的角色ID列表，不映射到数据库字段 */
    @TableField(exist = false)
    private List<Long> roleIds;
}
