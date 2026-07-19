package com.cgcpms.system.dict.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.system.dict.entity.SysDictData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysDictDataMapper extends BaseMapper<SysDictData> {

    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id, tenant_id, dict_type_id, dict_label, dict_value, css_class,
                   list_class, order_num, status, created_at, updated_at
            FROM sys_dict_data
            WHERE tenant_id = #{tenantId}
              AND dict_type_id = #{dictTypeId}
              AND status = 'ENABLE'
            ORDER BY order_num ASC
            """)
    List<SysDictData> selectEnabledByTypeAndTenant(
            @Param("dictTypeId") Long dictTypeId,
            @Param("tenantId") Long tenantId);
}
