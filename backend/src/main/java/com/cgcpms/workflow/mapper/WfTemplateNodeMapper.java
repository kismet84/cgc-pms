package com.cgcpms.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.workflow.entity.WfTemplateNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WfTemplateNodeMapper extends BaseMapper<WfTemplateNode> {

    @Select("SELECT COUNT(*) FROM wf_template_node WHERE template_id = #{templateId} AND node_code = #{nodeCode}")
    long countNodeCodeIncludingDeleted(@Param("templateId") Long templateId,
                                       @Param("nodeCode") String nodeCode);
}
