package com.cgcpms.notification.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Notification view object — exposes String-typed IDs for frontend JS compatibility.
 */
@Data
public class NotificationVO implements Serializable {

    private String id;
    private String tenantId;
    private String userId;
    private String title;
    private String content;
    private String bizType;
    private String bizId;
    private String notifyType;
    private Integer isRead;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime readTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    public static NotificationVO fromEntity(com.cgcpms.notification.entity.SysNotification entity) {
        if (entity == null) return null;
        NotificationVO vo = new NotificationVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setTenantId(String.valueOf(entity.getTenantId()));
        vo.setUserId(String.valueOf(entity.getUserId()));
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId() != null ? String.valueOf(entity.getBizId()) : null);
        vo.setNotifyType(entity.getNotifyType());
        vo.setIsRead(entity.getIsRead());
        vo.setReadTime(entity.getReadTime());
        vo.setCreatedTime(entity.getCreatedTime());
        return vo;
    }
}
