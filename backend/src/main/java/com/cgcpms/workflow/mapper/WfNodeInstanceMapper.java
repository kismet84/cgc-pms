package com.cgcpms.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.workflow.entity.WfNodeInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WfNodeInstanceMapper extends BaseMapper<WfNodeInstance> {

    @Select("SELECT * FROM wf_node_instance WHERE id=#{id} AND tenant_id=#{tenantId} AND deleted_flag=0 FOR UPDATE")
    WfNodeInstance selectByIdForUpdate(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
