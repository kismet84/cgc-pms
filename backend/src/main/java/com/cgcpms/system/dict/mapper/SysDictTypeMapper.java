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
            SELECT t.id, t.tenant_id, t.group_id, t.dict_code, t.dict_name, t.dict_class,
                   t.status, t.created_at, t.updated_at
            FROM sys_dict_type t
            JOIN sys_dict_group g
              ON g.tenant_id = t.tenant_id AND g.id = t.group_id
            WHERE t.tenant_id = #{tenantId}
              AND t.dict_code = #{dictCode}
              AND t.status = 'ENABLE'
              AND g.status = 'ENABLE'
            LIMIT 1
            """)
    SysDictType selectEnabledByCodeAndTenant(
            @Param("dictCode") String dictCode,
            @Param("tenantId") Long tenantId);
}
