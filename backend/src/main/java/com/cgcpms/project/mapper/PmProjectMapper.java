package com.cgcpms.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.project.entity.PmProject;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PmProjectMapper extends BaseMapper<PmProject> {

    @Delete("DELETE FROM pm_project WHERE id=#{id} AND tenant_id=#{tenantId}")
    int physicalDelete(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
