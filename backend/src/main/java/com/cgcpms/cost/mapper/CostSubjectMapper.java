package com.cgcpms.cost.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cgcpms.cost.entity.CostSubject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CostSubjectMapper extends BaseMapper<CostSubject> {

    @Select("""
            SELECT COUNT(*)
            FROM cost_subject
            WHERE tenant_id = #{tenantId}
              AND subject_code = #{subjectCode}
              AND account_category = #{accountCategory}
              AND deleted_flag = 0
              AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    Long countByTenantAndCode(@Param("tenantId") Long tenantId,
                              @Param("subjectCode") String subjectCode,
                              @Param("accountCategory") String accountCategory,
                              @Param("excludeId") Long excludeId);
}
