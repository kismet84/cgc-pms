package com.cgcpms.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.alert.entity.AlertLog;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AlertLogMapper extends BaseMapper<AlertLog> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT tenant_id
            FROM alert_log
            WHERE process_status = 'OPEN'
              AND deleted_flag = 0
              AND ((acknowledged_at IS NULL
                    AND response_due_at <= CURRENT_TIMESTAMP
                    AND escalation_level < 1)
                   OR (resolution_due_at <= CURRENT_TIMESTAMP
                       AND escalation_level < 2))
            ORDER BY tenant_id
            """)
    List<Long> selectPendingEscalationTenantIds();

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM alert_log
            WHERE tenant_id = #{tenantId}
              AND dedup_key = #{dedupKey}
              AND process_status = 'OPEN'
              AND deleted_flag = 0
            ORDER BY id
            FOR UPDATE
            """)
    List<AlertLog> selectOpenByDedupKey(@Param("tenantId") Long tenantId,
                                        @Param("dedupKey") String dedupKey);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE alert_log
            SET process_status = 'ARCHIVED',
                archived_at = #{archivedAt},
                archived_by = #{updatedBy},
                status_remark = #{statusRemark},
                updated_by = #{updatedBy},
                updated_at = #{archivedAt},
                version = version + 1
            WHERE id = #{id}
              AND tenant_id = #{tenantId}
              AND process_status = 'OPEN'
              AND deleted_flag = 0
            """)
    int archiveCashJournalAlert(@Param("id") Long id,
                                @Param("tenantId") Long tenantId,
                                @Param("archivedAt") LocalDateTime archivedAt,
                                @Param("statusRemark") String statusRemark,
                                @Param("updatedBy") Long updatedBy);
}
