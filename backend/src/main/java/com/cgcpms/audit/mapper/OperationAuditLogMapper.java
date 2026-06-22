package com.cgcpms.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.audit.entity.OperationAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationAuditLogMapper extends BaseMapper<OperationAuditLog> {
}
