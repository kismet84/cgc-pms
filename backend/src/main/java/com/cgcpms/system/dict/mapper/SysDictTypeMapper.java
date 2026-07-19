package com.cgcpms.system.dict.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.system.dict.entity.SysDictType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysDictTypeMapper extends BaseMapper<SysDictType> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, tenant_id, dict_code, dict_name, status, created_at, updated_at
            FROM sys_dict_type
            WHERE tenant_id = #{tenantId}
              AND dict_code = #{dictCode}
              AND status = 'ENABLE'
            LIMIT 1
            """)
    SysDictType selectEnabledByCodeAndTenant(
            @Param("dictCode") String dictCode,
            @Param("tenantId") Long tenantId);
}
