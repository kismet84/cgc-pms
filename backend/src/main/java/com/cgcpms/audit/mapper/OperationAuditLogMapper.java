package com.cgcpms.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.audit.entity.OperationAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OperationAuditLogMapper extends BaseMapper<OperationAuditLog> {

    @Insert("""
            INSERT INTO sys_operation_audit_log
              (tenant_id,user_id,operation_type,business_type,business_id,success_flag,before_snapshot,after_snapshot,created_at)
            VALUES
              (#{tenantId},#{userId},#{operationType},'WORKFLOW_TEMPLATE',#{templateId},1,#{beforeSnapshot},#{afterSnapshot},CURRENT_TIMESTAMP)
            """)
    int insertWorkflowTemplateAudit(@Param("tenantId") Long tenantId,
                                    @Param("userId") Long userId,
                                    @Param("operationType") String operationType,
                                    @Param("templateId") String templateId,
                                    @Param("beforeSnapshot") String beforeSnapshot,
                                    @Param("afterSnapshot") String afterSnapshot);
}
