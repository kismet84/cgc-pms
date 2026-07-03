package com.cgcpms.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("alert_notification_send_record")
public class AlertNotificationSendRecord implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long tenantId;

    private Long alertId;

    private String eventType;

    private String channel;

    private Long targetUserId;

    private Long bizNotificationId;

    private String sendStatus;

    private String failReason;

    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;
}
