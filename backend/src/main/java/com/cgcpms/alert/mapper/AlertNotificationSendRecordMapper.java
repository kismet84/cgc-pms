package com.cgcpms.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.cgcpms.alert.entity.AlertNotificationSendRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AlertNotificationSendRecordMapper extends BaseMapper<AlertNotificationSendRecord> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(*)
            FROM alert_notification_send_record
            WHERE tenant_id = #{tenantId}
              AND target_user_id = #{userId}
              AND alert_id = #{alertId}
              AND event_type = #{eventType}
              AND channel = 'IN_APP'
              AND send_status = 'SENT'
            """)
    Long countSentInApp(@Param("tenantId") Long tenantId,
                        @Param("userId") Long userId,
                        @Param("alertId") Long alertId,
                        @Param("eventType") String eventType);
}
