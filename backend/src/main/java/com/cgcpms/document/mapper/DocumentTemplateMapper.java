package com.cgcpms.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.common.util.DeletedCodeSource;
import com.cgcpms.document.entity.DocumentTemplate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentTemplateMapper extends BaseMapper<DocumentTemplate>, DeletedCodeSource {
    @Insert("INSERT INTO document_template_code_scope(tenant_id) VALUES(#{tenantId}) "
            + "ON DUPLICATE KEY UPDATE tenant_id=tenant_id")
    int ensureTenantCodeScope(@Param("tenantId") Long tenantId);

    @Select("SELECT tenant_id FROM document_template_code_scope WHERE tenant_id=#{tenantId} FOR UPDATE")
    Long lockTenantCodeScope(@Param("tenantId") Long tenantId);

    @Override
    @Select("SELECT template_code FROM biz_document_template "
            + "WHERE tenant_id=#{tenantId} AND template_code LIKE CONCAT(#{prefix}, '%') "
            + "ORDER BY template_code DESC LIMIT 1 FOR UPDATE")
    String selectLastCodeByPrefix(@Param("prefix") String prefix, @Param("tenantId") Long tenantId);
}
