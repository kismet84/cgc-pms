package com.cgcpms.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批抄送实体 — 不继承 BaseEntity（无 createdBy/updatedBy/deletedFlag）
 */
@Data
@TableName("wf_cc")
public class WfCc {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long instanceId;

    private Long ccUserId;

    private String ccUserName;

    private String businessType;

    private Long businessId;

    private String title;

    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdTime;
}
