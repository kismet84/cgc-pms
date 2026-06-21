package com.cgcpms.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long tenantId;

    @NotBlank
    @Size(max = 50)
    private String roleCode;

    @NotBlank
    private String roleName;

    private String roleType;

    private String status;

    private String dataScope;

    /** 角色等级：0=SUPER_ADMIN, 1=ADMIN, 2=普通角色。用于防止越级提权。 */
    private Integer roleLevel;
}
