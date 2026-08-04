package com.cgcpms.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.project.entity.PmProject;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PmProjectMapper extends BaseMapper<PmProject> {

    @Select("SELECT * FROM pm_project WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0 FOR UPDATE")
    PmProject selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);

    @Delete("DELETE FROM pm_project WHERE id=#{id} AND tenant_id=#{tenantId}")
    int physicalDelete(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
