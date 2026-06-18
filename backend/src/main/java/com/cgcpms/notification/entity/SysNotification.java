package com.cgcpms.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * System notification entity — maps to sys_notification table (V37).
 *
 * <p>Does NOT extend BaseEntity because sys_notification has minimalist columns
 * with {@code created_time} instead of {@code created_at} and no
 * {@code updated_by/updated_at/deleted_flag/remark} columns.</p>
 */
@Data
@TableName("sys_notification")
public class SysNotification implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** Tenant ID (explicit, never read from UserContext). */
    private Long tenantId;

    /** Receiver user ID. */
    private Long userId;

    /** Notification title. */
    private String title;

    /** Notification content. */
    private String content;

    /** Business type: WORKFLOW_APPROVAL, WORKFLOW_REJECT, WORKFLOW_CC, ALERT, SYSTEM. */
    private String bizType;

    /** Business ID (approval instance / alert id etc.). */
    private Long bizId;

    /** Notification type: INFO, WARNING, ERROR. */
    private String notifyType;

    /** Read flag: 0 = unread, 1 = read. */
    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;

    @TableField(value = "created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
}
