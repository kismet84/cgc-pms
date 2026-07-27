package com.cgcpms.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.workflow.entity.WfTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WfTemplateMapper extends BaseMapper<WfTemplate> {
    @Select("SELECT * FROM wf_template WHERE id = #{id} AND tenant_id = #{tenantId} FOR UPDATE")
    WfTemplate selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
