package com.cgcpms.org.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cgcpms.common.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("org_department")
public class OrgDepartment extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long companyId;

    private Long parentId;

    private String deptCode;

    private String deptName;

    private Integer orderNum;

    private String status;

    /** 数据库审计列 created_time — 由 MyMetaObjectHandler 自动填充 */
    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    /** 数据库审计列 updated_time — 由 MyMetaObjectHandler 自动填充 */
    @TableField("updated_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    /** 覆盖 BaseEntity.createdAt，避免与 createdTime 的列映射冲突 */
    @TableField(exist = false)
    private LocalDateTime createdAt;

    /** 覆盖 BaseEntity.updatedAt，避免与 updatedTime 的列映射冲突 */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
