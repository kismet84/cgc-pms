package com.cgcpms.site.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.site.entity.SiteDailyLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SiteDailyLogMapper extends BaseMapper<SiteDailyLog> {
    @Select("SELECT * FROM site_daily_log WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted_flag = 0 FOR UPDATE")
    SiteDailyLog selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
