package com.cgcpms.overhead.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.overhead.entity.OverheadAllocationRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OverheadAllocationRunMapper extends BaseMapper<OverheadAllocationRun> {

    /**
     * 定时线程没有认证租户，只在这里跨租户发现需要执行的活跃租户。
     * 后续规则、项目、成本和执行事实仍在显式租户上下文中查询与写入。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT tenant_id
            FROM pm_project
            WHERE status = 'ACTIVE'
              AND deleted_flag = 0
            ORDER BY tenant_id
            """)
    List<Long> selectActiveTenantIds();
}
