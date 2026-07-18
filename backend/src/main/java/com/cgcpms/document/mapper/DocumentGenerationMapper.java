package com.cgcpms.document.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.document.entity.DocumentGeneration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DocumentGenerationMapper extends BaseMapper<DocumentGeneration> {
    @Select("""
            SELECT id FROM biz_document_generation
             WHERE tenant_id = #{tenantId} AND deleted_flag = 0
               AND status IN ('PENDING','RENDERING') AND requested_at < #{cutoff}
             ORDER BY requested_at LIMIT 100
            """)
    List<Long> selectStaleIds(@Param("tenantId") Long tenantId, @Param("cutoff") LocalDateTime cutoff);

    @Select("""
            SELECT g.id FROM biz_document_generation g
            LEFT JOIN sys_file f ON f.id = g.file_id AND f.tenant_id = g.tenant_id AND f.deleted_flag = 0
             WHERE g.tenant_id = #{tenantId} AND g.deleted_flag = 0
               AND g.status = 'SUCCEEDED' AND f.id IS NULL
             ORDER BY g.requested_at LIMIT 100
            """)
    List<Long> selectBrokenSuccessfulIds(@Param("tenantId") Long tenantId);

    @Select("""
            SELECT f.id FROM sys_file f
            LEFT JOIN biz_document_generation g
              ON g.file_id = f.id AND g.tenant_id = f.tenant_id AND g.status = 'SUCCEEDED' AND g.deleted_flag = 0
             WHERE f.tenant_id = #{tenantId} AND f.deleted_flag = 0
               AND f.document_type = 'GENERATED_DOCUMENT' AND g.id IS NULL
             ORDER BY f.created_at LIMIT 100
            """)
    List<Long> selectOrphanGeneratedFileIds(@Param("tenantId") Long tenantId);
}
